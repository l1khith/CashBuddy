use crate::classifier::categories::{model_label_to_category, MODEL_LABELS};
use crate::classifier::tokenizer::WordPieceTokenizer;
use crate::{Category, CategoryScore, ClassificationResult, CoreError};
use ort::session::Session;
use ort::value::Tensor;
use std::path::{Path, PathBuf};
use std::sync::Mutex;

pub struct TransactionClassifier {
    session: Mutex<Option<Session>>,
    tokenizer: WordPieceTokenizer,
    label_map: Vec<String>,
}

impl TransactionClassifier {
    pub fn new(model_path: String) -> Result<Self, CoreError> {
        let label_map: Vec<String> = MODEL_LABELS.iter().map(|&s| s.to_string()).collect();

        let model_file = Path::new(&model_path);
        let session = if !model_path.is_empty() && model_file.exists() {
            let sess = Session::builder()
                .map_err(|e| {
                    log::error!("Failed to create ORT Session builder: {}", e);
                    CoreError::ModelLoadError
                })?
                .with_intra_threads(1)
                .map_err(|e| {
                    log::error!("Failed to set intra threads: {}", e);
                    CoreError::ModelLoadError
                })?
                .commit_from_file(model_file)
                .map_err(|e| {
                    log::error!("Failed to load ONNX model from '{}': {}", model_path, e);
                    CoreError::ModelLoadError
                })?;
            Some(sess)
        } else {
            log::warn!(
                "Model file not found at '{}', TransactionClassifier will operate in fallback mode",
                model_path
            );
            None
        };

        // Try to load vocab.txt from the same directory as model_path
        let vocab_path = match model_file.parent() {
            Some(p) => p.join("vocab.txt"),
            None => PathBuf::from("vocab.txt"),
        };

        let tokenizer = if vocab_path.exists() {
            match WordPieceTokenizer::from_file(&vocab_path) {
                Ok(tok) => tok,
                Err(e) => {
                    log::warn!(
                        "Failed to load vocab from '{}': {:?}, using builtin",
                        vocab_path.display(),
                        e
                    );
                    WordPieceTokenizer::new_builtin()
                }
            }
        } else {
            WordPieceTokenizer::new_builtin()
        };

        Ok(Self {
            session: Mutex::new(session),
            tokenizer,
            label_map,
        })
    }

    pub fn classify(&self, text: String) -> Result<ClassificationResult, CoreError> {
        let trimmed = text.trim();
        if trimmed.is_empty() {
            return Err(CoreError::InferenceError);
        }

        let mut session_guard = self.session.lock().map_err(|_| CoreError::InferenceError)?;

        if let Some(ref mut session) = *session_guard {
            let tokenized = self.tokenizer.encode(trimmed, 128)?;

            let mut input_ids_arr = ndarray::Array2::<i64>::zeros((1, 128));
            let mut mask_arr = ndarray::Array2::<i64>::zeros((1, 128));

            for i in 0..128 {
                if let Some(&id) = tokenized.input_ids.get(i) {
                    input_ids_arr[[0, i]] = id;
                }
                if let Some(&mask) = tokenized.attention_mask.get(i) {
                    mask_arr[[0, i]] = mask;
                }
            }

            let input_ids_tensor = Tensor::from_array(input_ids_arr).map_err(|e| {
                log::error!("Failed to build input_ids tensor: {}", e);
                CoreError::InferenceError
            })?;
            let mask_tensor = Tensor::from_array(mask_arr).map_err(|e| {
                log::error!("Failed to build attention_mask tensor: {}", e);
                CoreError::InferenceError
            })?;

            let outputs = session
                .run(ort::inputs![
                    "input_ids" => input_ids_tensor,
                    "attention_mask" => mask_tensor
                ])
                .map_err(|e| {
                    log::error!("ORT model inference failed: {}", e);
                    CoreError::InferenceError
                })?;

            let (_shape, logits_slice) = outputs["logits"]
                .try_extract_tensor::<f32>()
                .map_err(|e| {
                    log::error!("Failed to extract logits from output tensor: {}", e);
                    CoreError::InferenceError
                })?;

            let probs = softmax(logits_slice);

            let mut all_scores = Vec::with_capacity(self.label_map.len());
            let mut best_category = Category::Unknown;
            let mut best_score = -1.0f32;

            for (idx, label) in self.label_map.iter().enumerate() {
                let cat = model_label_to_category(label);
                let score = match probs.get(idx) {
                    Some(&s) => s,
                    None => 0.0,
                };
                if score > best_score {
                    best_score = score;
                    best_category = cat;
                }
                all_scores.push(CategoryScore {
                    category: cat,
                    score,
                });
            }

            Ok(ClassificationResult {
                category: best_category,
                confidence: best_score.max(0.0),
                all_scores,
            })
        } else {
            self.classify_fallback(trimmed)
        }
    }

    fn classify_fallback(&self, text: &str) -> Result<ClassificationResult, CoreError> {
        let text_lower = text.to_lowercase();
        let detected = if text_lower.contains("food")
            || text_lower.contains("swiggy")
            || text_lower.contains("zomato")
            || text_lower.contains("starbucks")
            || text_lower.contains("restaurant")
        {
            Category::Food
        } else if text_lower.contains("uber") || text_lower.contains("ola") || text_lower.contains("transport") {
            Category::Transport
        } else if text_lower.contains("shopping") || text_lower.contains("amazon") || text_lower.contains("flipkart") {
            Category::Shopping
        } else if text_lower.contains("electricity") || text_lower.contains("bill") || text_lower.contains("recharge") {
            Category::Bills
        } else if text_lower.contains("salary") || text_lower.contains("payroll") {
            Category::Salary
        } else {
            Category::Unknown
        };

        let mut all_scores = Vec::with_capacity(self.label_map.len());
        for label in &self.label_map {
            let cat = model_label_to_category(label);
            let score = if cat == detected { 0.85 } else { 0.02 };
            all_scores.push(CategoryScore {
                category: cat,
                score,
            });
        }

        Ok(ClassificationResult {
            category: detected,
            confidence: 0.85,
            all_scores,
        })
    }
}

pub fn softmax(values: &[f32]) -> Vec<f32> {
    let max_val = values.iter().cloned().fold(f32::NEG_INFINITY, f32::max);
    let exps: Vec<f32> = values.iter().map(|&v| (v - max_val).exp()).collect();
    let sum: f32 = exps.iter().sum();
    if sum == 0.0 {
        return vec![0.0; values.len()];
    }
    exps.iter().map(|&v| v / sum).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_classifier_fallback() {
        let classifier = TransactionClassifier::new("non_existent_model.onnx".to_string()).unwrap();
        let res = classifier.classify("swiggy food delivery".to_string()).unwrap();
        assert_eq!(res.category, Category::Food);
        assert!(res.confidence > 0.80);
    }

    #[test]
    fn test_classifier_with_quantized_model() {
        let model_path = "../androidApp/src/main/assets/models/distilbert-transaction-classifier-quantized.onnx".to_string();
        if std::path::Path::new(&model_path).exists() {
            let classifier = TransactionClassifier::new(model_path).unwrap();
            let res = classifier.classify("STARBUCKS STORE #1234".to_string()).unwrap();
            assert_eq!(res.category, Category::Food);
            assert!(res.confidence > 0.90);
        }
    }
}
