import { localScreenRepository } from './repository';
import type { ScreenRepository } from './repository';
import type { CreateDigitalScreenInput, UpdateDigitalScreenInput } from './types';

/**
 * Stable application-facing façade. PR 1 can replace this repository binding with
 * an HTTP implementation without changing list/editor/viewer callers.
 */
export const screenRepository: ScreenRepository = localScreenRepository;

export const listDigitalScreens = () => screenRepository.list();
export const getDigitalScreen = (id: string) => screenRepository.get(id);
export const createDigitalScreen = (input: CreateDigitalScreenInput) => screenRepository.create(input);
export const updateDigitalScreen = (id: string, input: UpdateDigitalScreenInput) => screenRepository.update(id, input);
export const publishDigitalScreen = (id: string) => screenRepository.publish(id);
export const unpublishDigitalScreen = (id: string) => screenRepository.unpublish(id);
export const duplicateDigitalScreen = (id: string) => screenRepository.duplicate(id);
export const deleteDigitalScreen = (id: string) => screenRepository.remove(id);
