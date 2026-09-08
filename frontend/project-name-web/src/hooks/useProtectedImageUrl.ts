// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-31
import { useEffect, useState } from 'react'

import { fetchProtectedBlob } from '@/api/client'

type ProtectedImageState = {
  url: string
  loading: boolean
  error: boolean
}

export function useProtectedImageUrl(path: string | null | undefined): ProtectedImageState {
  const [state, setState] = useState<ProtectedImageState>({
    url: '',
    loading: Boolean(path),
    error: false,
  })

  useEffect(() => {
    let active = true
    let objectUrl = ''
    if (!path) {
      setState({ url: '', loading: false, error: false })
      return () => undefined
    }
    setState({ url: '', loading: true, error: false })
    void fetchProtectedBlob(path)
      .then((blob) => {
        if (!active) return
        objectUrl = URL.createObjectURL(blob)
        setState({ url: objectUrl, loading: false, error: false })
      })
      .catch(() => {
        if (active) setState({ url: '', loading: false, error: true })
      })
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [path])

  return state
}
