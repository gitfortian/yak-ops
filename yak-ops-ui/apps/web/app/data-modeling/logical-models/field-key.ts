let fieldSequence = 0;

/** Row identity for one field draft; keeps React state attached across reorders. */
export const newFieldKey = () => `field-${(fieldSequence += 1)}`;
