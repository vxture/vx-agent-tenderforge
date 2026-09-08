import { useNavigate } from 'react-router'

import { Button, ResultPageTemplate } from '@vxture/design-system'

export default function Error403() {
  const navigate = useNavigate()

  const handleBackHome = () => {
    navigate('/')
  }

  return (
    <ResultPageTemplate
      className="min-h-dvh"
      tone="warning"
      icon="shield-warning"
      title="没有访问权限"
      description="当前账号不能访问这个页面。"
      actions={<Button onClick={handleBackHome}>返回首页</Button>}
    />
  )
}
