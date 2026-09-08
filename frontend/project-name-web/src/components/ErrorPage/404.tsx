import { useNavigate } from 'react-router'

import { Button, ResultPageTemplate } from '@vxture/design-system'

export default function Error404() {
  const navigate = useNavigate()

  const handleBackHome = () => {
    navigate('/')
  }

  return (
    <ResultPageTemplate
      className="min-h-dvh"
      icon="search"
      title="页面不存在"
      description="该地址无对应页面，或页面已经被移动。"
      actions={<Button onClick={handleBackHome}>返回首页</Button>}
    />
  )
}
