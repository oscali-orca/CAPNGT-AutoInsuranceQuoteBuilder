import { useState, type FormEvent } from 'react'
import './LoginPage.css'
import { ApiError, signIn } from '../../services/quoteApi'


interface LoginPageProps {
  onSignIn: () => void
}

function LoginPage({ onSignIn }: LoginPageProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [passwordVisible, setPasswordVisible] = useState(false)
  const [error, setError] = useState('')
  const [isSigningIn, setIsSigningIn] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (isSigningIn) return
    setError('')
    setIsSigningIn(true)
    try {
      await signIn(username, password)
      onSignIn()
    } catch (error) {
      setError(error instanceof ApiError && error.status === 401
        ? 'Username or password is incorrect.'
        : error instanceof Error ? error.message : 'Could not sign in. Please try again.')
    } finally {
      setIsSigningIn(false)
    }
  }

  return (
    <main className="login-page">
      <a className="brand" href="/" aria-label="CAPNGT home">
        <span className="brand-mark" aria-hidden="true">
          C
        </span>
        <span>CAPNGT</span>
      </a>

      <section className="login-card" aria-labelledby="login-title">
        <div className="heading-block">
          <p className="eyebrow">WELCOME BACK</p>
          <h1 id="login-title">Sign in to your account</h1>
          <p>Enter your details to continue to your insurance quote.</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <label htmlFor="username">Username</label>
          <input
            autoComplete="username"
            id="username"
            name="username"
            onChange={(event) => setUsername(event.target.value)}
            placeholder="alice.training"
            required
            type="text"
            value={username}
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

          <button className="primary-button" type="submit" disabled={isSigningIn}>
            {isSigningIn ? 'Signing in...' : 'Sign in'} <span aria-hidden="true">→</span>
          </button>
        </form>

        <p className="security-note">
          Sign in with your assigned training account.
        </p>
      </section>

      <footer className="form-footer">
        <span>© 2026 CAPNGT</span>
        <span>Auto Insurance Quote Builder</span>
      </footer>
    </main>
  )
}

export default LoginPage
