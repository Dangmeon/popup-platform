import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useApp } from '../context/AppContext.jsx'

export default function Login(){
  const app = useApp()
  const nav = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function handleSubmit(e){
    e.preventDefault()
    app.login({ email, password })
    nav('/')
  }

  return (
    <div className="form-box">
      <h2>로그인</h2>
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>이메일</label>
          <input type="email" value={email} onChange={e=>setEmail(e.target.value)} required />
        </div>
        <div className="form-group">
          <label>비밀번호</label>
          <input type="password" value={password} onChange={e=>setPassword(e.target.value)} required />
        </div>
        <button className="btn btn-primary btn-block" type="submit">로그인</button>
      </form>
      <div className="form-foot">계정이 없으신가요? <Link to="/signup">회원가입</Link></div>
    </div>
  )
}
