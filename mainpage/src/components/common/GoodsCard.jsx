import React from 'react'
import { useApp } from '../../context/AppContext.jsx'

export default function GoodsCard({ item }){
  const app = useApp()
  const liked = app.likedProducts.includes(item.id)
  return (
    <div className="card goods-card">
      <div className="popup-card-img-wrap">
        <img src={item.image} alt={item.name} />
        <button
          className="like-btn"
          onClick={()=>app.toggleLikeProduct(item.id)}
        >{liked ? '♥' : '♡'}</button>
      </div>
      <div className="card-body">
        <div className="card-title">{item.name}</div>
        <div className="card-sub">{item.price.toLocaleString()}원</div>
        <button
          className="btn btn-primary btn-block"
          style={{marginTop:10}}
          disabled={item.stock<=0}
          onClick={()=>app.addToCart(item,1)}
        >{item.stock<=0 ? '품절' : '장바구니 담기'}</button>
      </div>
    </div>
  )
}
