import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useApp } from '../context/AppContext.jsx'

export default function Signup(){
  const app = useApp()
  const nav = useNavigate()
  const [type, setType] = useState('user')
  const [form, setForm] = useState({ email:'', password:'', name:'', phone:'' })

  function update(key, val){ setForm(f => ({ ...f, [key]: val })) }

  function handleSubmit(e){
    e.preventDefault()
    app.signup({ ...form, type })
    nav('/')
  }

  return (
    <div className="form-box">
      <h2>회원가입</h2>
      <div className="form-switch">
        <button type="button" className={type==='user'?'active':''} onClick={()=>setType('user')}>일반회원</button>
        <button type="button" className={type==='brand'?'active':''} onClick={()=>setType('brand')}>브랜드회원</button>
      </div>
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>이메일</label>
          <input type="email" value={form.email} onChange={e=>update('email', e.target.value)} required />
        </div>
        <div className="form-group">
          <label>비밀번호</label>
          <input type="password" value={form.password} onChange={e=>update('password', e.target.value)} required />
        </div>
        <div className="form-group">
          <label>이름 {type==='brand' && '(브랜드명)'}</label>
          <input value={form.name} onChange={e=>update('name', e.target.value)} required />
        </div>
        <div className="form-group">
          <label>연락처</label>
          <input value={form.phone} onChange={e=>update('phone', e.target.value)} required />
        </div>
        <button className="btn btn-primary btn-block" type="submit">가입하기</button>
      </form>
      <div className="form-foot">이미 계정이 있으신가요? <Link to="/login">로그인</Link></div>
    </div>
  )
}
