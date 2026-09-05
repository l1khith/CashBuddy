use crate::CoreError;
use std::collections::HashMap;
use std::path::Path;

pub struct TokenizedOutput {
    pub input_ids: Vec<i64>,
    pub attention_mask: Vec<i64>,
}

pub struct WordPieceTokenizer {
    vocab: HashMap<String, i64>,
    cls_token_id: i64,
    sep_token_id: i64,
    unk_token_id: i64,
    pad_token_id: i64,
}

fn is_bert_punctuation(c: char) -> bool {
    c.is_ascii_punctuation() || ('\u{2000}'..='\u{206F}').contains(&c)
}

fn basic_tokenize(text: &str) -> Vec<String> {
    let mut tokens = Vec::new();
    let mut current = String::new();
    for ch in text.chars() {
        if ch.is_whitespace() {
            if !current.is_empty() {
                tokens.push(current);
                current = String::new();
            }
        } else if is_bert_punctuation(ch) {
            if !current.is_empty() {
                tokens.push(current);
                current = String::new();
            }
            tokens.push(ch.to_string());
        } else {
            current.push(ch);
        }
    }
    if !current.is_empty() {
        tokens.push(current);
    }
    tokens
}

impl WordPieceTokenizer {
    pub fn new_builtin() -> Self {
        let mut vocab = HashMap::new();
        vocab.insert("[PAD]".to_string(), 0);
        vocab.insert("[UNK]".to_string(), 100);
        vocab.insert("[CLS]".to_string(), 101);
        vocab.insert("[SEP]".to_string(), 102);
        vocab.insert("[MASK]".to_string(), 103);

        Self {
            vocab,
            cls_token_id: 101,
            sep_token_id: 102,
            unk_token_id: 100,
            pad_token_id: 0,
        }
    }

    pub fn from_file<P: AsRef<Path>>(path: P) -> Result<Self, CoreError> {
        let content = std::fs::read_to_string(path).map_err(|_| CoreError::TokenizerError)?;
        Self::from_str(&content)
    }

    pub fn from_str(content: &str) -> Result<Self, CoreError> {
        let mut vocab = HashMap::with_capacity(31_000);
        for (idx, line) in content.lines().enumerate() {
            let token = line.trim();
            if !token.is_empty() {
                vocab.insert(token.to_string(), idx as i64);
            }
        }

        let cls_token_id = match vocab.get("[CLS]") {
            Some(&id) => id,
            None => 101,
        };
        let sep_token_id = match vocab.get("[SEP]") {
            Some(&id) => id,
            None => 102,
        };
        let unk_token_id = match vocab.get("[UNK]") {
            Some(&id) => id,
            None => 100,
        };
        let pad_token_id = match vocab.get("[PAD]") {
            Some(&id) => id,
            None => 0,
        };

        Ok(Self {
            vocab,
            cls_token_id,
            sep_token_id,
            unk_token_id,
            pad_token_id,
        })
    }

    pub fn encode(&self, text: &str, max_len: usize) -> Result<TokenizedOutput, CoreError> {
        if max_len < 2 {
            return Err(CoreError::InferenceError);
        }
        let mut input_ids = Vec::with_capacity(max_len);
        let mut attention_mask = Vec::with_capacity(max_len);

        input_ids.push(self.cls_token_id);
        attention_mask.push(1);

        let lower = text.to_lowercase();
        let tokens = basic_tokenize(&lower);

        for word in tokens {
            if input_ids.len() >= max_len.saturating_sub(1) {
                break;
            }

            // WordPiece subword tokenization
            let chars: Vec<char> = word.chars().collect();
            if chars.len() > 100 {
                input_ids.push(self.unk_token_id);
                attention_mask.push(1);
                continue;
            }

            let mut is_bad = false;
            let mut start = 0;
            let mut sub_tokens = Vec::new();

            while start < chars.len() {
                let mut end = chars.len();
                let mut cur_substr_id = None;
                while start < end {
                    let substr: String = chars[start..end].iter().collect();
                    let piece = if start > 0 {
                        format!("##{}", substr)
                    } else {
                        substr
                    };
                    if let Some(&id) = self.vocab.get(&piece) {
                        cur_substr_id = Some(id);
                        break;
                    }
                    end -= 1;
                }
                if let Some(id) = cur_substr_id {
                    sub_tokens.push(id);
                    start = end;
                } else {
                    is_bad = true;
                    break;
                }
            }

            if is_bad {
                input_ids.push(self.unk_token_id);
                attention_mask.push(1);
            } else {
                for id in sub_tokens {
                    if input_ids.len() >= max_len.saturating_sub(1) {
                        break;
                    }
                    input_ids.push(id);
                    attention_mask.push(1);
                }
            }
        }

        input_ids.push(self.sep_token_id);
        attention_mask.push(1);

        // Pad sequence to max_len
        while input_ids.len() < max_len {
            input_ids.push(self.pad_token_id);
            attention_mask.push(0);
        }

        Ok(TokenizedOutput {
            input_ids,
            attention_mask,
        })
    }
}

impl Default for WordPieceTokenizer {
    fn default() -> Self {
        Self::new_builtin()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encode_max_len_guard() {
        let tokenizer = WordPieceTokenizer::new_builtin();
        assert!(tokenizer.encode("test", 0).is_err());
        assert!(tokenizer.encode("test", 1).is_err());
        assert!(tokenizer.encode("test", 2).is_ok());
    }
}
