import React, { createContext, useContext, useEffect, useState } from 'react'
import { popups as initialPopups, products as initialProducts } from '../data/sampleData.js'

const AppContext = createContext(null)
export const useApp = () => useContext(AppContext)

function loadLS(key, fallback){
  try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback } catch { return fallback }
}

export function AppProvider({ children }){
  const [popupsData] = useState(initialPopups)
  const [productsData] = useState(initialProducts)
  const [currentUser, setCurrentUser] = useState(() => loadLS('popQ_user', null))
  const [likedPopups, setLikedPopups] = useState(() => loadLS('popQ_liked_popups', []))
  const [likedProducts, setLikedProducts] = useState(() => loadLS('popQ_liked_products', []))
  const [cart, setCart] = useState(() => loadLS('popQ_cart', []))
  const [reservations, setReservations] = useState(() => loadLS('popQ_reservations', []))
  const [waitings, setWaitings] = useState(() => loadLS('popQ_waitings', []))
  const [toasts, setToasts] = useState([])
  const [mascotVisible, setMascotVisible] = useState(false)

  useEffect(()=>localStorage.setItem('popQ_user', JSON.stringify(currentUser)), [currentUser])
  useEffect(()=>localStorage.setItem('popQ_liked_popups', JSON.stringify(likedPopups)), [likedPopups])
  useEffect(()=>localStorage.setItem('popQ_liked_products', JSON.stringify(likedProducts)), [likedProducts])
  useEffect(()=>localStorage.setItem('popQ_cart', JSON.stringify(cart)), [cart])
  useEffect(()=>localStorage.setItem('popQ_reservations', JSON.stringify(reservations)), [reservations])
  useEffect(()=>localStorage.setItem('popQ_waitings', JSON.stringify(waitings)), [waitings])

  function showToast(msg){
    const id = Date.now()
    setToasts(t => [...t, { id, msg }])
    setTimeout(()=> setToasts(t => t.filter(x=>x.id!==id)), 2500)
  }

  function signup({ email, password, name, phone, type }){
    const user = { email, name, phone, type: type || 'user' }
    setCurrentUser(user)
    showToast('회원가입이 완료되었습니다')
    return user
  }
  function login({ email, password }){
    const user = { email, name: email.split('@')[0], type: email.includes('brand') ? 'brand' : 'user' }
    setCurrentUser(user)
    showToast('로그인되었습니다')
    return user
  }
  function logout(){ setCurrentUser(null); showToast('로그아웃되었습니다') }

  function toggleLikePopup(id){
    setLikedPopups(prev => prev.includes(id) ? prev.filter(x=>x!==id) : [...prev, id])
  }
  function toggleLikeProduct(id){
    setLikedProducts(prev => prev.includes(id) ? prev.filter(x=>x!==id) : [...prev, id])
  }

  function addToCart(product, quantity=1){
    setCart(prev => {
      const existing = prev.find(i=>i.productId===product.id)
      if(existing){
        return prev.map(i=> i.productId===product.id ? { ...i, quantity:i.quantity+quantity } : i)
      }
      return [...prev, { productId:product.id, quantity }]
    })
    setMascotVisible(true)
    showToast('장바구니에 담았어요')
  }
  function updateCartQty(productId, quantity){
    if(quantity<=0){ removeFromCart(productId); return }
    setCart(prev => prev.map(i=> i.productId===productId ? { ...i, quantity } : i))
  }
  function removeFromCart(productId){
    setCart(prev => prev.filter(i=>i.productId!==productId))
  }
  function clearCart(){ setCart([]) }

  function createReservation(popupId, timeSlot, peopleCount){
    const id = 'R' + Date.now()
    const expiresAt = Date.now() + 10*60*1000
    setReservations(prev => [...prev, { id, popupId, timeSlot, peopleCount, status:'HOLD', expiresAt }])
    return id
  }
  function confirmReservation(id){
    setReservations(prev => prev.map(r=> r.id===id ? { ...r, status:'CONFIRMED' } : r))
    showToast('예약이 확정되었습니다')
  }
  function cancelReservation(id){
    setReservations(prev => prev.map(r=> r.id===id ? { ...r, status:'CANCELLED' } : r))
    showToast('예약이 취소되었습니다')
  }

  function registerWaiting(popupId, peopleCount){
    const id = 'W' + Date.now()
    const queueNumber = Math.floor(Math.random()*20)+1
    setWaitings(prev => [...prev, { id, popupId, peopleCount, queueNumber, status:'WAITING', checkinCode: Math.floor(1000+Math.random()*9000) }])
    return id
  }
  function cancelWaiting(id){
    setWaitings(prev => prev.map(w=> w.id===id ? { ...w, status:'CANCELLED' } : w))
  }

  const value = {
    popups: popupsData, products: productsData,
    currentUser, login, logout, signup,
    likedPopups, likedProducts, toggleLikePopup, toggleLikeProduct,
    cart, addToCart, updateCartQty, removeFromCart, clearCart,
    reservations, createReservation, confirmReservation, cancelReservation,
    waitings, registerWaiting, cancelWaiting,
    toasts, showToast,
    mascotVisible, setMascotVisible,
  }

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>
}
