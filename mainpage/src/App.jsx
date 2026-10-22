import React, { useState, useEffect } from 'react'
import { Routes, Route } from 'react-router-dom'
import Header from './components/Header.jsx'
import Footer from './components/Footer.jsx'
import Home from './pages/Home.jsx'
import PopupList from './pages/PopupList.jsx'
import PopupDetail from './pages/PopupDetail.jsx'
import Wishlist from './pages/Wishlist.jsx'
import MyPage from './pages/MyPage.jsx'
import Cart from './pages/Cart.jsx'
import Login from './pages/Login.jsx'
import Signup from './pages/Signup.jsx'
import { useApp } from './context/AppContext.jsx'
import LoadingMascot from './components/common/LoadingMascot.jsx'
import NotFound from './pages/NotFound.jsx'

export default function App(){
  const app = useApp()
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const timer = setTimeout(() => setLoading(false), 1200)
    return () => clearTimeout(timer)
  }, [])

  if (loading) {
    return <LoadingMascot text="POPQ 불러오는 중..." />
  }

  return (
    <div className="app">
      <Header />
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/popups" element={<PopupList />} />
          <Route path="/popups/:popupId" element={<PopupDetail />} />
          <Route path="/wishlist" element={<Wishlist />} />
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/cart" element={<Cart />} />
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
      <Footer />
      <div className="toast-wrap">
        {app.toasts.map(t => <div className="toast" key={t.id}>{t.msg}</div>)}
      </div>
    </div>
  )
}
