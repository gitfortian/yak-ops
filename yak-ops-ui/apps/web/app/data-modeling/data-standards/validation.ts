import type { OwnerOption, StandardInput } from "@/service/data-modeling/data-standards/types";
export const STANDARD_CODE = /^[a-z][a-z0-9_]{0,63}$/;
export function validateStandard(input: StandardInput, owners: readonly OwnerOption[]) {
  const errors: Partial<Record<keyof StandardInput, string>> = {};
  if (!input.cnName.trim() || input.cnName.trim().length > 128)
    errors.cnName = "请输入1至128个字符的中文名称";
  if (!STANDARD_CODE.test(input.enCode))
    errors.enCode = "须以小写字母开头，仅含小写字母、数字或下划线，最多64位";
  const value = input.lengthPrecision;
  if (value !== null && value !== "") {
    if (input.dataType === "STRING") {
      if (!/^[1-9][0-9]{0,9}$/.test(value) || Number(value) > 2147483647)
        errors.lengthPrecision = "请输入有效正整数长度";
    } else if (input.dataType === "DECIMAL") {
      const match = /^([1-9][0-9]?),([0-9]{1,2})$/.exec(value);
      if (!match || Number(match[1]) > 38 || Number(match[2]) > Number(match[1]))
        errors.lengthPrecision = "请输入合法精度，例如18,2";
    } else errors.lengthPrecision = "当前类型不支持长度或精度";
  }
  if (!owners.some((owner) => owner.userId === input.ownerId))
    errors.ownerId = "请选择当前工作空间有效成员";
  if ((input.businessCaliber?.length ?? 0) > 2000)
    errors.businessCaliber = "业务口径不能超过2000个字符";
  return errors;
}
