import { useState, type FormEvent } from 'react'
import './LoginPage.css'

const DEMO_EMAIL = 'demo@capngt.com'
const DEMO_PASSWORD = 'Demo123!'

interface LoginPageProps {
  onSignIn: () => void
}

function LoginPage({ onSignIn }: LoginPageProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordVisible, setPasswordVisible] = useState(false)
  const [error, setError] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')

    if (email.trim().toLowerCase() !== DEMO_EMAIL || password !== DEMO_PASSWORD) {
      setError('That email and password do not match the demo account.')
      return
    }

    onSignIn()
  }

  return (
    <main className="login-page">
      <section className="brand-panel" aria-label="Auto insurance quote builder">
        <a className="brand" href="/" aria-label="CAPNGT home">
          <span className="brand-mark" aria-hidden="true">
            C
          </span>
          <span>CAPNGT</span>
        </a>

        <div className="brand-copy">
          <span className="eyebrow">AUTO INSURANCE, MADE CLEAR</span>
          <h1>A better way to find the right coverage.</h1>
          <p>
            Compare your options, understand your price, and build a quote with
            confidence.
          </p>
        </div>

        <div className="brand-footer">
          <span className="status-dot" aria-hidden="true" />
          A simple, transparent quote experience
        </div>
        <div className="decorative-circle decorative-circle-large" aria-hidden="true" />
        <div className="decorative-circle decorative-circle-small" aria-hidden="true" />
      </section>

      <section className="form-panel">
        <div className="login-card">
          <div className="mobile-brand" aria-hidden="true">
            <span className="brand-mark">C</span>
            <span>CAPNGT</span>
          </div>

          <div className="heading-block">
            <p className="eyebrow">WELCOME BACK</p>
            <h2>Sign in to your account</h2>
            <p>Enter your details to continue to your insurance quote.</p>
          </div>

          <form className="login-form" onSubmit={handleSubmit}>
            <label htmlFor="email">Email address</label>
            <input
              autoComplete="username"
              id="email"
              name="email"
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              required
              type="email"
              value={email}
            />

            <div className="password-label-row">
              <label htmlFor="password">Password</label>
              <button
                aria-label={passwordVisible ? 'Hide password' : 'Show password'}
                className="text-button"
                onClick={() => setPasswordVisible((visible) => !visible)}
                type="button"
              >
                {passwordVisible ? 'Hide' : 'Show'}
              </button>
            </div>
            <input
              autoComplete="current-password"
              id="password"
              name="password"
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Enter your password"
              required
              type={passwordVisible ? 'text' : 'password'}
              value={password}
            />

            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}

            <button className="primary-button" type="submit">
              Sign in <span aria-hidden="true">→</span>
            </button>
          </form>

          <aside className="demo-note">
            <strong>Demo access</strong>
            <span>
              Email: <code>{DEMO_EMAIL}</code>
            </span>
            <span>
              Password: <code>{DEMO_PASSWORD}</code>
            </span>
          </aside>

          <p className="security-note">
            Demo only. This form does not connect to an authentication service.
          </p>
        </div>
        <footer className="form-footer">
          <span>© 2026 CAPNGT</span>
          <span>Auto Insurance Quote Builder</span>
        </footer>
      </section>
    </main>
  )
}

export default LoginPage
