import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { useApp } from '../context/AppContext.jsx'
import PopupCard from '../components/common/PopupCard.jsx'
import GoodsCard from '../components/common/GoodsCard.jsx'

export default function Home(){
  
  const app = useApp()
  const [heroIndex, setHeroIndex] = useState(0)

  const heroSlides = [...app.popups].sort((a,b)=>b.likes-a.likes).slice(0,3)
  const popular = [...app.popups].sort((a,b)=>b.likes-a.likes).slice(0,8)
  const soon = [...app.popups].sort((a,b)=> new Date(a.endDate)-new Date(b.endDate)).slice(0,8)
  const goods = app.products.slice(0,4)

  const hero = heroSlides[heroIndex] || heroSlides[0]

  return (
    <div>
      {hero && (
        <section className="hero-slide" style={{ backgroundImage: `url(${hero.image})` }}>
          <Link to={`/popups/${hero.id}`} className="hero-slide-link">
            <div className="hero-slide-overlay">
              <span className="badge">{hero.category}</span>
              <h1>{hero.title}</h1>
              <p>{hero.location} · {hero.startDate} ~ {hero.endDate}</p>
            </div>
          </Link>
          <div className="hero-dots">
            {heroSlides.map((s,i)=>(
              <button
                key={s.id}
                className={'hero-dot'+(i===heroIndex?' active':'')}
                onClick={()=>setHeroIndex(i)}
                aria-label={`슬라이드 ${i+1}`}
              />
            ))}
          </div>
        </section>
      )}

      <div className="container">
        <div className="section-title">🔥 지금 인기 있는 팝업</div>
        <div className="grid-4">
          {popular.map(p => <PopupCard key={p.id} popup={p} />)}
        </div>

        <div className="section-title">⏰ 마감 임박 팝업</div>
        <div className="grid-4">
          {soon.map(p => <PopupCard key={p.id} popup={p} />)}
        </div>

        <div className="section-title">🛍️ 인기 굿즈</div>
        <div className="grid-4">
          {goods.map(g => <GoodsCard key={g.id} item={g} />)}
        </div>
      </div>
    </div>
  )
}
