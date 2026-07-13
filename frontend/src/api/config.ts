import { client } from './client';
import type { AppConfig } from './types';

export async function getConfig(): Promise<AppConfig> {
  const { data } = await client.get('/api/config');
  return data;
}
