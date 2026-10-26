import type { OwnerOption, StandardInput } from "@/service/data-modeling/data-standards/types";
export const STANDARD_CODE = /^[a-z][a-z0-9_]{0,63}$/;
const DECIMAL_PAIR = /^([1-9][0-9]?),([0-9]{1,2})$/;
/** Integer digits of a complete `p,s` DECIMAL pair, or null while the pair is incomplete or invalid. */
export function decimalIntegerDigits(value: string | null) {
  const match = value ? DECIMAL_PAIR.exec(value) : null;
  if (!match) return null;
  const precision = Number(match[1]);
  const scale = Number(match[2]);
  return precision > 38 || scale > precision ? null : precision - scale;
}
export function validateStandard(input: StandardInput, owners: readonly OwnerOption[]) {
  const errors: Partial<Record<keyof StandardInput, string>> = {};
  if (!input.cnName.trim() || input.cnName.trim().length > 128)
    errors.cnName = "请输入1至128个字符的中文名称";
  if (!STANDARD_CODE.test(input.enCode))
    errors.enCode = "须以小写字母开头，仅含小写字母、数字或下划线，最多64位";
  const value = input.lengthPrecision;
  if (input.dataType === "DECIMAL") {
    // The server refuses an incomplete pair because physical types cannot size a DECIMAL without it.
    const match = value ? DECIMAL_PAIR.exec(value) : null;
    if (!match) errors.lengthPrecision = "请填写总位数与小数位";
    else if (Number(match[1]) > 38 || Number(match[2]) > Number(match[1]))
      errors.lengthPrecision = "总位数不超过38，且小数位不得超过总位数";
  } else if (value !== null && value !== "") {
    if (input.dataType === "STRING") {
      if (!/^[1-9][0-9]{0,9}$/.test(value) || Number(value) > 2147483647)
        errors.lengthPrecision = "请输入有效正整数长度";
    } else errors.lengthPrecision = "当前类型不支持长度或精度";
  }
  if (!owners.some((owner) => owner.userId === input.ownerId))
    errors.ownerId = "请选择当前工作空间有效成员";
  if ((input.businessCaliber?.length ?? 0) > 2000)
    errors.businessCaliber = "业务口径不能超过2000个字符";
  if ((input.defaultValue?.length ?? 0) > 2048)
    errors.defaultValue = "业务预设值不能超过2048个字符";
  return errors;
}
