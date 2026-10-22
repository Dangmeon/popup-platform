import React from 'react'
import { Link } from 'react-router-dom'
import { useApp } from '../context/AppContext.jsx'

export default function Cart(){
  const app = useApp()
  const items = app.cart.map(c => ({ ...c, product: app.products.find(p=>p.id===c.productId) })).filter(i=>i.product)
  const total = items.reduce((sum,i)=> sum + i.product.price*i.quantity, 0)

  return (
    <div className="container">
      <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between' }}>
        <div className="page-title" style={{ margin:'32px 0' }}>장바구니</div>
        {items.length > 0 && (
          <img
            src="/mascot-cart.png"
            alt="쇼핑 중인 너구리"
            className="cart-mascot-bounce"
          />
        )}
      </div>

      {items.length===0 ? (
        <div className="empty-state cart-empty">
          <img src="/mascot-empty.png" alt="빈 장바구니 너구리" className="cart-empty-mascot" />
          <p>장바구니가 비어있어요.</p>
          <Link to="/popups" className="btn btn-primary" style={{ marginTop:16, display:'inline-block' }}>
            팝업 구경하러 가기
          </Link>
        </div>
      ) : (
        <>
          {items.map(i => (
            <div className="cart-row" key={i.productId}>
              <img src={i.product.image} alt="" />
              <div style={{flex:1}}>
                <div className="card-title">{i.product.name}</div>
                <div className="card-sub">{i.product.price.toLocaleString()}원</div>
              </div>
              <div className="qty-box">
                <button onClick={()=>app.updateCartQty(i.productId, i.quantity-1)}>-</button>
                <span>{i.quantity}</span>
                <button onClick={()=>app.updateCartQty(i.productId, i.quantity+1)}>+</button>
              </div>
              <button className="btn btn-outline" onClick={()=>app.removeFromCart(i.productId)}>삭제</button>
            </div>
          ))}
          <div className="cart-summary">
            <div style={{display:'flex', justifyContent:'space-between', fontWeight:700, fontSize:18}}>
              <span>총 결제금액</span>
              <span>{total.toLocaleString()}원</span>
            </div>
            <button className="btn btn-primary btn-block" style={{marginTop:16}}>주문하기 (데모)</button>
          </div>
        </>
      )}
    </div>
  )
}
