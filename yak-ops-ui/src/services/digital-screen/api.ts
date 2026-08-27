import { httpScreenRepository } from './repository';
import type { ScreenRepository } from './repository';
import type { CreateDigitalScreenInput, UpdateDigitalScreenInput } from './types';

/** Stable application-facing façade backed by the server-side Digital Screen repository. */
export const screenRepository: ScreenRepository = httpScreenRepository;

export const listDigitalScreens = () => screenRepository.list();
export const getDigitalScreen = (id: string) => screenRepository.get(id);
export const createDigitalScreen = (input: CreateDigitalScreenInput) => screenRepository.create(input);
export const updateDigitalScreen = (id: string, input: UpdateDigitalScreenInput) => screenRepository.update(id, input);
export const publishDigitalScreen = (id: string) => screenRepository.publish(id);
export const unpublishDigitalScreen = (id: string) => screenRepository.unpublish(id);
export const duplicateDigitalScreen = (id: string) => screenRepository.duplicate(id);
export const deleteDigitalScreen = (id: string) => screenRepository.remove(id);
