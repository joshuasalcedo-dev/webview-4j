//! The protocol's frames (PROTOCOL.md §1): `MSG <subject> <header-length> <payload-length>\r\n`,
//! a header block, then the payload. Decoding checks in the order of §1.2, so every
//! implementation names the same error for the same bad frame.

pub const MAX_SUBJECT: usize = 256;
pub const MAX_HEADERS: u64 = 64 * 1024;
pub const MAX_PAYLOAD: u64 = 64 * 1024 * 1024;
const MAX_LINE: usize = 300;

/// A message: where it goes, what describes it, and bytes nobody in the middle reads.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Message {
    pub subject: String,
    pub headers: Vec<(String, String)>,
    pub payload: Vec<u8>,
}

/// Why a frame was refused; [`FrameError::kind`] is the name the fixtures use.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FrameError {
    BadLine,
    BadSubject,
    BadLength,
    TooLarge,
    Truncated,
    BadHeader,
}

impl FrameError {
    pub fn kind(self) -> &'static str {
        match self {
            FrameError::BadLine => "bad-line",
            FrameError::BadSubject => "bad-subject",
            FrameError::BadLength => "bad-length",
            FrameError::TooLarge => "too-large",
            FrameError::Truncated => "truncated",
            FrameError::BadHeader => "bad-header",
        }
    }
}

impl Message {
    pub fn new(subject: impl Into<String>) -> Self {
        Message { subject: subject.into(), headers: Vec::new(), payload: Vec::new() }
    }

    pub fn header(mut self, name: &str, value: impl Into<String>) -> Self {
        self.headers.push((name.to_string(), value.into()));
        self
    }

    pub fn payload(mut self, payload: impl Into<Vec<u8>>) -> Self {
        self.payload = payload.into();
        self
    }

    /// The first value of header `name`, compared without regard to case.
    pub fn get(&self, name: &str) -> Option<&str> {
        self.headers.iter().find(|(n, _)| n.eq_ignore_ascii_case(name)).map(|(_, v)| v.as_str())
    }

    pub fn encode(&self) -> Vec<u8> {
        let mut block = Vec::new();
        for (name, value) in &self.headers {
            block.extend_from_slice(name.as_bytes());
            block.extend_from_slice(b": ");
            block.extend_from_slice(value.as_bytes());
            block.extend_from_slice(b"\r\n");
        }
        block.extend_from_slice(b"\r\n");
        let mut frame = format!("MSG {} {} {}\r\n", self.subject, block.len(), self.payload.len()).into_bytes();
        frame.extend_from_slice(&block);
        frame.extend_from_slice(&self.payload);
        frame
    }
}

pub fn decode(frame: &[u8]) -> Result<Message, FrameError> {
    // 1. the line
    let end = frame
        .windows(2)
        .take(MAX_LINE + 1)
        .position(|pair| pair == b"\r\n")
        .ok_or(FrameError::BadLine)?;
    let line = &frame[..end];
    if !line.iter().all(|&b| (0x20..0x7f).contains(&b)) {
        return Err(FrameError::BadLine);
    }
    let line = std::str::from_utf8(line).map_err(|_| FrameError::BadLine)?;
    let parts: Vec<&str> = line.split(' ').collect();
    let [verb, subject, header_length, payload_length] = parts.as_slice() else {
        return Err(FrameError::BadLine);
    };
    if *verb != "MSG" || subject.is_empty() || header_length.is_empty() || payload_length.is_empty() {
        return Err(FrameError::BadLine);
    }
    // 2. the subject
    if subject.len() > MAX_SUBJECT {
        return Err(FrameError::TooLarge);
    }
    if !valid_subject(subject) {
        return Err(FrameError::BadSubject);
    }
    // 3. the lengths
    let header_length = length(header_length)?;
    let payload_length = length(payload_length)?;
    if header_length < 2 {
        return Err(FrameError::BadLength);
    }
    // 4. the limits, from the line alone
    if header_length > MAX_HEADERS || payload_length > MAX_PAYLOAD {
        return Err(FrameError::TooLarge);
    }
    // 5. exactly that many bytes follow
    let rest = &frame[end + 2..];
    let needed = (header_length + payload_length) as usize;
    if rest.len() < needed {
        return Err(FrameError::Truncated);
    }
    if rest.len() > needed {
        return Err(FrameError::BadLength);
    }
    // 6. the header block
    let (block, payload) = rest.split_at(header_length as usize);
    Ok(Message { subject: subject.to_string(), headers: headers(block)?, payload: payload.to_vec() })
}

fn length(digits: &str) -> Result<u64, FrameError> {
    let plain = digits.bytes().all(|b| b.is_ascii_digit()) && (digits == "0" || !digits.starts_with('0'));
    if !plain {
        return Err(FrameError::BadLength);
    }
    digits.parse::<u64>().ok().filter(|&n| n <= i64::MAX as u64).ok_or(FrameError::BadLength)
}

/// A header block: `Name: value\r\n` lines, then an empty line (PROTOCOL.md §3).
pub fn headers(block: &[u8]) -> Result<Vec<(String, String)>, FrameError> {
    let text = std::str::from_utf8(block).map_err(|_| FrameError::BadHeader)?;
    let lines = text.strip_suffix("\r\n").ok_or(FrameError::BadHeader)?;
    if !lines.is_empty() && !lines.ends_with("\r\n") {
        return Err(FrameError::BadHeader);
    }
    let mut headers = Vec::new();
    for line in lines.split_terminator("\r\n") {
        if line.contains(['\r', '\n']) {
            return Err(FrameError::BadHeader);
        }
        let (name, value) = line.split_once(':').ok_or(FrameError::BadHeader)?;
        if name.is_empty() || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-') {
            return Err(FrameError::BadHeader);
        }
        headers.push((name.to_string(), value.trim_matches([' ', '\t']).to_string()));
    }
    Ok(headers)
}

/// Tokens of `A-Z a-z 0-9 - _` separated by single dots, at most 256 bytes (PROTOCOL.md §2).
pub fn valid_subject(subject: &str) -> bool {
    subject.len() <= MAX_SUBJECT && subject.split('.').all(valid_token)
}

pub fn valid_token(token: &str) -> bool {
    !token.is_empty() && token.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
}

#[cfg(test)]
mod tests {
    use super::*;
    use base64::Engine;
    use serde_json::Value;

    fn fixture(name: &str) -> Value {
        let path = format!("{}/../../protocol/fixtures/{name}", env!("CARGO_MANIFEST_DIR"));
        serde_json::from_str(&std::fs::read_to_string(&path).unwrap_or_else(|e| panic!("{path}: {e}"))).unwrap()
    }

    fn bytes(case: &Value, text: &str, base64: &str) -> Vec<u8> {
        match (case.get(text), case.get(base64)) {
            (Some(Value::String(s)), _) => s.as_bytes().to_vec(),
            (_, Some(Value::String(b))) => base64::engine::general_purpose::STANDARD.decode(b).unwrap(),
            _ => panic!("case has neither {text} nor {base64}: {case}"),
        }
    }

    fn pairs(value: &Value) -> Vec<(String, String)> {
        value
            .as_array()
            .unwrap()
            .iter()
            .map(|pair| (pair[0].as_str().unwrap().to_string(), pair[1].as_str().unwrap().to_string()))
            .collect()
    }

    #[test]
    fn decodes_every_fixture_frame() {
        for case in fixture("frames.json")["decode"].as_array().unwrap() {
            let message = decode(&bytes(case, "frame", "frameBase64")).unwrap_or_else(|e| panic!("{}: {e:?}", case["name"]));
            let expect = &case["expect"];
            assert_eq!(message.subject, expect["subject"], "{}", case["name"]);
            assert_eq!(message.headers, pairs(&expect["headers"]), "{}", case["name"]);
            assert_eq!(message.payload, bytes(expect, "payload", "payloadBase64"), "{}", case["name"]);
        }
    }

    #[test]
    fn encodes_every_fixture_message_exactly() {
        for case in fixture("frames.json")["encode"].as_array().unwrap() {
            let message = Message {
                subject: case["subject"].as_str().unwrap().to_string(),
                headers: pairs(&case["headers"]),
                payload: case["payload"].as_str().unwrap().as_bytes().to_vec(),
            };
            assert_eq!(String::from_utf8(message.encode()).unwrap(), case["frame"].as_str().unwrap(), "{}", case["name"]);
            assert_eq!(decode(&message.encode()).unwrap(), message, "{} round-trips", case["name"]);
        }
    }

    #[test]
    fn refuses_every_fixture_error_with_its_kind() {
        for case in fixture("frames.json")["errors"].as_array().unwrap() {
            let error = decode(case["frame"].as_str().unwrap().as_bytes()).expect_err(case["name"].as_str().unwrap());
            assert_eq!(error.kind(), case["error"], "{}", case["name"]);
        }
    }

    #[test]
    fn subjects_and_header_blocks_match_the_fixtures() {
        let subjects = fixture("subjects.json");
        for subject in subjects["valid"].as_array().unwrap() {
            assert!(valid_subject(subject.as_str().unwrap()), "{subject}");
        }
        for subject in subjects["invalid"].as_array().unwrap() {
            assert!(!valid_subject(subject.as_str().unwrap()), "{subject}");
        }
        let blocks = fixture("headers.json");
        for case in blocks["valid"].as_array().unwrap() {
            assert_eq!(headers(case["block"].as_str().unwrap().as_bytes()).unwrap(), pairs(&case["headers"]), "{case}");
        }
        for block in blocks["invalid"].as_array().unwrap() {
            assert_eq!(headers(block.as_str().unwrap().as_bytes()), Err(FrameError::BadHeader), "{block}");
        }
    }
}
