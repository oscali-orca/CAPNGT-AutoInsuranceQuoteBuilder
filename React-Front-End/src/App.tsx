import { useState } from 'react'
import LoginPage from './features/auth/LoginPage'

function App() {
  const [signedIn, setSignedIn] = useState(false)

  if (signedIn) {
    return (
      <main className="login-page" style={{ gridTemplateColumns: '1fr' }}>
        <section className="form-panel">
          <div className="login-card" role="status">
            <div className="heading-block">
              <p className="eyebrow">DEMO SESSION</p>
              <h2>You’re signed in</h2>
              <p>
                Demo sign-in succeeded. Authentication is not connected to a
                backend service.
              </p>
            </div>
            <button
              className="primary-button"
              onClick={() => setSignedIn(false)}
              type="button"
            >
              Sign out
            </button>
          </div>
        </section>
      </main>
    )
  }

  return <LoginPage onSignIn={() => setSignedIn(true)} />
}

export default App
