import { useNavigate } from 'react-router'

import { Button, ResultPageTemplate } from '@vxture/design-system'

export default function Error500() {
  const navigate = useNavigate()

  const handleBackHome = () => {
    navigate('/')
  }

  return (
    <ResultPageTemplate
      className="min-h-dvh"
      tone="danger"
      icon="server"
      title="服务器繁忙"
      description="服务暂时无法处理请求，请稍后重试。"
      actions={<Button onClick={handleBackHome}>返回首页</Button>}
    />
  )
}
