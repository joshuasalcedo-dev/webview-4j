import "./authentication.css"

/**
 * How strong a new password is, as it is typed: a bar and a word, and what would make it stronger. A guide for
 * sign-up and password changes; the server decides what it accepts.
 */
type Strength = { score: 0 | 1 | 2 | 3 | 4; label: string; advice?: string }

const COMMON = ["password", "123456", "qwerty", "letmein", "welcome", "admin", "iloveyou", "monkey", "dragon", "abc123"]

/** A password's strength: its length and its kinds of characters, less for common words and repeats. */
function strengthOf(password: string): Strength {
  if (!password) return { score: 0, label: "", advice: undefined }
  const kinds = [/[a-z]/, /[A-Z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((r) => r.test(password)).length
  let points = Math.min(password.length, 20) / 4 + kinds
  if (COMMON.some((w) => password.toLowerCase().includes(w))) points -= 4
  if (/(.)\1{2,}/.test(password)) points -= 2
  const score = (points < 3 ? 0 : points < 5 ? 1 : points < 7 ? 2 : points < 8.5 ? 3 : 4) as Strength["score"]
  const label = ["Too weak", "Weak", "Fair", "Good", "Strong"][score]
  const advice =
    password.length < 12
      ? "Longer is stronger: 12 characters or more."
      : kinds < 3
        ? "Mix in capitals, numbers or symbols."
        : score < 3
          ? "Avoid common words and repeats."
          : undefined
  return { score, label, advice }
}

function PasswordStrength({ password, className }: { password: string; className?: string }) {
  const s = strengthOf(password)
  if (!password) return null
  return (
    <div className={["nt-strength", className].filter(Boolean).join(" ")} data-score={s.score} aria-live="polite">
      <div className="nt-strength-bar" role="meter" aria-valuemin={0} aria-valuemax={4} aria-valuenow={s.score} aria-label="Password strength">
        {[0, 1, 2, 3].map((i) => (
          <span key={i} data-on={i < s.score || (s.score === 0 && i === 0) ? "" : undefined} />
        ))}
      </div>
      <span className="nt-strength-label">{s.label}</span>
      {s.advice && <span className="nt-strength-advice">{s.advice}</span>}
    </div>
  )
}

export { PasswordStrength, strengthOf }
export type { Strength }
