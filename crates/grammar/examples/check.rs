//! `cargo run -p grammar --example check -- "This is an test."` (or the text on stdin; `--dialect en-GB` first).
use std::io::Read;
use std::time::Instant;

fn main() {
    let mut args: Vec<String> = std::env::args().skip(1).collect();
    let mut dialect = grammar::Dialect::American;
    if args.first().map(String::as_str) == Some("--dialect") && args.len() >= 2 {
        dialect = grammar::Dialect::from_tag(&args[1]);
        args.drain(..2);
    }
    let text = if args.is_empty() {
        let mut s = String::new();
        std::io::stdin().read_to_string(&mut s).expect("stdin");
        s
    } else {
        args.join(" ")
    };
    let started = Instant::now();
    let checker = grammar::Checker::new(dialect);
    let loaded = started.elapsed();
    let started = Instant::now();
    let mistakes = checker.check(&text);
    let took = started.elapsed();
    let units: Vec<u16> = text.encode_utf16().collect();
    for m in &mistakes {
        let found = String::from_utf16_lossy(&units[m.from..m.to]);
        println!("{:>4}..{:<4} {:<12} \"{found}\": {}  ->  {:?}", m.from, m.to, m.kind, m.message, m.suggestions);
    }
    println!("{} mistake(s) in {took:?} ({dialect:?}; the checker loaded in {loaded:?})", mistakes.len());
}
