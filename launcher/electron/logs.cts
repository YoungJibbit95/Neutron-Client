import fs from 'node:fs/promises';
import { owned, redact } from './files.cjs';
import type { LogRecord } from './contracts.cjs';
export class Logs {
  lines: LogRecord[] = []; private id = 0; private queue = Promise.resolve(); private failure: Error | null = null; private pending: LogRecord[] = []; private scheduled = false;
  constructor(private root: string, private notify: () => void) {}
  add(source: 'client' | 'build', message: string, level: 'info' | 'warn' | 'error' = 'info') {
    const safe = redact(message.replace(/\x1b\[[0-?]*[ -/]*[@-~]/g, '').slice(0, 65536));
    const line: LogRecord = { id: ++this.id, time: new Date().toISOString(), source, level, message: { de: safe, en: safe } };
    this.lines = [...this.lines, line].slice(-2000); this.notify();
    this.pending.push(line);
    this.schedule();
  }
  private schedule() {
    if (!this.scheduled) {
      this.scheduled = true;
      this.queue = this.queue.then(async () => {
        while (this.pending.length) {
          const batch = this.pending.splice(0, 64);
          for (const stream of ['client', 'build'] as const) {
            let text = '';
            for (const item of batch.filter(item => item.source === stream)) {
              const next = `${item.time} [${item.level.toUpperCase()}] ${item.message.en}\n`;
              if (Buffer.byteLength(text + next) > 512 * 1024) { await this.append(stream, text); text = ''; }
              text += next;
            }
            if (text) await this.append(stream, text);
          }
        }
      }).catch(error => { this.failure = error instanceof Error ? error : new Error(String(error)); this.pending = []; }).finally(() => { this.scheduled = false; if (this.pending.length) this.schedule(); });
    }
  }
  private async append(source: 'client' | 'build', text: string) {
      const file = await owned(this.root, 'logs', source + '.log'); await fs.mkdir(await owned(this.root, 'logs'), { recursive: true });
      let size = 0; try { size = (await fs.stat(file)).size; } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
      if (size + Buffer.byteLength(text) > 2 * 1024 ** 2) {
        for (let index = 4; index >= 0; index--) {
          const previous = index === 0 ? file : file + '.' + index; const next = file + '.' + (index + 1);
          try { if (index === 4) await fs.unlink(next).catch(error => { if (error.code !== 'ENOENT') throw error; }); await fs.rename(previous, next); }
          catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
        }
      }
      await fs.appendFile(file, text, 'utf8');
  }
  async flush() { do { await this.queue; } while (this.scheduled || this.pending.length); if (this.failure) throw this.failure; }
  async restore() {
    const restored: Omit<LogRecord, 'id'>[] = [];
    for (const source of ['client', 'build'] as const) {
      const text = await this.export(source);
      for (const row of text.split('\n').slice(-2001)) {
        const match = /^(\d{4}-\d{2}-\d{2}T[^ ]+) \[(INFO|WARN|ERROR)\] (.*)$/.exec(row);
        if (match && Number.isFinite(Date.parse(match[1]))) restored.push({ source, time: match[1], level: match[2].toLowerCase() as LogRecord['level'], message: { de: match[3], en: match[3] } });
      }
    }
    this.lines = restored.sort((a, b) => a.time.localeCompare(b.time)).slice(-2000).map(line => ({ ...line, id: ++this.id }));
  }
  async export(source: 'client' | 'build') {
    await this.flush(); let result = '';
    for (let index = 5; index >= 0; index--) {
      const file = await owned(this.root, 'logs', source + '.log' + (index ? '.' + index : ''));
      try { result += await fs.readFile(file, 'utf8'); } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
    }
    return redact(result);
  }
}
