use std::path::Path;

const KNOWN_ROOT_PATHS: &[&str] = &[
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/system/su",
    "/system/bin/failsafe/su",
    "/data/local/xbin/su",
    "/data/local/bin/su",
    "/data/local/su",
    "/system/sd/xbin/su",
    "/system/bin/failsafe/su",
    "/data/local/su",
];

pub struct SecurityValidator;

impl SecurityValidator {
    pub fn new() -> Self {
        Self
    }

    /// Returns true if device shows signs of being rooted or compromised
    pub fn is_device_compromised(&self) -> bool {
        // Check for su binaries
        for path_str in KNOWN_ROOT_PATHS {
            if Path::new(path_str).exists() {
                return true;
            }
        }

        // Check for test-keys in build properties if accessible
        if let Ok(build_tags) = std::env::var("RO_BUILD_TAGS") {
            if build_tags.contains("test-keys") {
                return true;
            }
        }

        false
    }
}

impl Default for SecurityValidator {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_validator_instantiation() {
        let validator = SecurityValidator::new();
        // On standard dev machine, su should not exist in /system/bin
        let _ = validator.is_device_compromised();
    }
}
