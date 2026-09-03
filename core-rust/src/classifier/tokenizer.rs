use crate::CoreError;
use std::collections::HashMap;

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

impl WordPieceTokenizer {
    pub fn new_builtin() -> Self {
        let mut vocab = HashMap::new();
        // Common basic tokens
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

    pub fn encode(&self, text: &str, max_len: usize) -> Result<TokenizedOutput, CoreError> {
        let mut input_ids = Vec::with_capacity(max_len);
        let mut attention_mask = Vec::with_capacity(max_len);

        input_ids.push(self.cls_token_id);
        attention_mask.push(1);

        // Simple whitespace tokenization for sequence preparation
        for word in text.split_whitespace() {
            if input_ids.len() >= max_len.saturating_sub(1) {
                break;
            }
            let token_id = self.vocab.get(word).copied().unwrap_or(self.unk_token_id);
            input_ids.push(token_id);
            attention_mask.push(1);
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
