import React, { useState, useMemo } from 'react'
import { useApp } from '../context/AppContext.jsx'
import PopupCard from '../components/common/PopupCard.jsx'

export default function PopupList(){
  const app = useApp()
  const [category, setCategory] = useState('전체')
  const [keyword, setKeyword] = useState('')

  const categories = useMemo(()=>{
    const set = new Set(app.popups.map(p=>p.category))
    return ['전체', ...Array.from(set)]
  }, [app.popups])

  const filtered = app.popups.filter(p => {
    const matchCategory = category === '전체' || p.category === category
    const matchKeyword = !keyword || p.title.includes(keyword) || p.location.includes(keyword)
    return matchCategory && matchKeyword
  })

  return (
    <div className="container">
      <div className="page-title">팝업 탐색</div>

      <input
        placeholder="팝업명, 지역으로 검색"
        value={keyword}
        onChange={e=>setKeyword(e.target.value)}
        style={{ width:'100%', maxWidth:420, padding:'12px 18px', borderRadius:999, border:'1px solid var(--border)', marginBottom:20, outline:'none' }}
      />

      <div className="tabs" style={{ flexWrap:'wrap' }}>
        {categories.map(c => (
          <button
            key={c}
            className={'tab-btn'+(category===c?' active':'')}
            onClick={()=>setCategory(c)}
          >{c}</button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <div className="empty-state">조건에 맞는 팝업이 없어요.</div>
      ) : (
        <div className="grid-4">
          {filtered.map(p => <PopupCard key={p.id} popup={p} />)}
        </div>
      )}
    </div>
  )
}
