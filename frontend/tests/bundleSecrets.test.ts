import { mkdtempSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { build } from 'vite'

function filesIn(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name)
    return statSync(full).isDirectory() ? filesIn(full) : [full]
  })
}

// Constitution II: no secret may reach the React bundle.
describe('production bundle (Constitution II)', () => {
  it('contains no API key variable names, tokens, or placeholder keys', async () => {
    const outDir = mkdtempSync(join(tmpdir(), 'caramel-bundle-'))
    await build({ logLevel: 'silent', mode: 'production', build: { outDir, emptyOutDir: true } })

    const files = filesIn(outDir)
    expect(files.some((f) => f.endsWith('.js'))).toBe(true) // the build really produced a bundle

    const forbidden = ['GOOGLE_AI_STUDIO_API_KEY', 'AGENT_API_TOKEN', 'your-key-here', 'test-key-not-real', 'X-Agent-Token']
    const realKey = process.env.GOOGLE_AI_STUDIO_API_KEY
    if (realKey) forbidden.push(realKey)
    const agentToken = process.env.AGENT_API_TOKEN
    if (agentToken) forbidden.push(agentToken)

    for (const file of files) {
      const text = readFileSync(file, 'utf8')
      for (const secret of forbidden) {
        expect(text.includes(secret), `${file} must not contain "${secret.slice(0, 6)}…"`).toBe(false)
      }
    }
  })
})
