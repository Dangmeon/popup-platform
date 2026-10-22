import React from 'react'
import ErrorPage from '../../pages/ErrorPage.jsx'

export default class ErrorBoundary extends React.Component {
  constructor(props){
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError(){
    return { hasError: true }
  }

  componentDidCatch(error, info){
    console.error('앱에서 에러 발생:', error, info)
  }

  render(){
    if (this.state.hasError) {
      return <ErrorPage />
    }
    return this.props.children
  }
}
