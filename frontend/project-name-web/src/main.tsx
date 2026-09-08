import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import App from '@/App'

import '@vxture/design-system/styles/globals.css'
import '@vxture/design-system/styles/brands/vxture.css'
import '@/styles/globals.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
)
