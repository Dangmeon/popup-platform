import React from 'react'
import { useApp } from '../context/AppContext.jsx'

export default function Toasts(){
  const app = useApp()
  return (
    <div className="toast-wrap">
      {app.toasts.map(t => (
        <div key={t.id} className="toast">{t.text}</div>
      ))}
    </div>
  )
}
