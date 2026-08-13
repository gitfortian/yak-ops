import { API_SUCCESS_CODE } from "@/services/http/response";
import { useIntl } from "@umijs/max";
import { Button, Drawer, Form, message } from "antd";
import { forwardRef, useImperativeHandle, useRef, useState } from "react";

import { dataSourceGroupList } from "../constants";
import DatabaseIcons from "../icon/DatabaseIcons";
import {
  createDataSource,
  testDataSourceConnectionWithParams,
  updateDataSource,
} from "../service";
import type {
  DataSourceFormValues,
  DataSourceModalOpenPayload,
  DataSourceModalRef,
  DataSourceRecord,
} from "../types";
import { DataSourceOperateType } from "../types";
import {
  buildSubmitPayload,
  normalizeConnectionFormValues,
  parseOriginalJson,
} from "../utils";
import DataSourceTypeSelector from "./DataSourceTypeSelector";
import DynamicDataSourceForm from "./DynamicDataSourceForm";
import "./DataSourceEditorDrawer.less";

const DRAWER_WIDTH = 620;

const AddOrEditDataSourceModal = forwardRef<DataSourceModalRef>((_, ref) => {
  const intl = useIntl();
  const [basicForm] = Form.useForm<DataSourceFormValues>();
  const [configForm] = Form.useForm();
  const [open, setOpen] = useState(false);
  const [operateType, setOperateType] = useState(DataSourceOperateType.Create);
  const [currentRecord, setCurrentRecord] = useState<DataSourceRecord>();
  const [selectedDbType, setSelectedDbType] = useState("");
  const [showFormStep, setShowFormStep] = useState(false);
  const [hideBackButton, setHideBackButton] = useState(false);
  const [testing, setTesting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const successCallbackRef = useRef<(() => void) | undefined>();

  const isCreateMode = operateType === DataSourceOperateType.Create;
  const isEditMode = operateType === DataSourceOperateType.Edit;
  const busy = testing || submitting;

  const resetEditorState = () => {
    setCurrentRecord(undefined);
    setSelectedDbType("");
    setShowFormStep(false);
    setHideBackButton(false);
    setTesting(false);
    setSubmitting(false);
    successCallbackRef.current = undefined;
    basicForm.resetFields();
    configForm.resetFields();
  };

  const handleClose = () => {
    if (busy) return;
    setOpen(false);
  };

  const handleAfterOpenChange = (visible: boolean) => {
    if (!visible) {
      resetEditorState();
    }
  };

  const initializeEditForm = (record: DataSourceRecord) => {
    basicForm.setFieldsValue({
      name: record.name || "",
      environment: record.environment || "",
      remark: record.remark || "",
    });
  };

  useImperativeHandle(ref, () => ({
    open: ({
      operateType: nextOperateType,
      currentRecord: nextRecord,
      onSuccess,
      dbType,
      hideBack,
    }: DataSourceModalOpenPayload) => {
      resetEditorState();
      successCallbackRef.current = onSuccess;
      setOperateType(nextOperateType);
      setCurrentRecord(nextRecord);

      if (nextOperateType === DataSourceOperateType.Edit && nextRecord) {
        setSelectedDbType(nextRecord.dbType || "");
        setShowFormStep(true);
        setHideBackButton(true);
        initializeEditForm(nextRecord);
      } else if (nextOperateType === DataSourceOperateType.Create && dbType) {
        setSelectedDbType(dbType);
        setShowFormStep(true);
        setHideBackButton(Boolean(hideBack));
      }

      setOpen(true);
    },
    close: handleClose,
  }));

  const handleSelectDbType = (dbType: string) => {
    basicForm.resetFields();
    configForm.resetFields();
    setSelectedDbType(dbType);
    setShowFormStep(true);
    setHideBackButton(false);
  };

  const handleBackToTypeSelection = () => {
    if (busy) return;
    setShowFormStep(false);
    setSelectedDbType("");
    setHideBackButton(false);
    basicForm.resetFields();
    configForm.resetFields();
  };

  const handleTestConnection = async () => {
    if (testing || submitting) return;

    try {
      setTesting(true);
      const connectionValues = normalizeConnectionFormValues(
        await configForm.validateFields(),
      );
      const response = await testDataSourceConnectionWithParams({
        dataSourceId: isEditMode ? currentRecord?.id : undefined,
        dbType: selectedDbType,
        connJson: JSON.stringify({
          ...connectionValues,
          dbType: selectedDbType,
        }),
      });

      if (response.code === API_SUCCESS_CODE && response.data === true) {
        message.success("连接测试成功");
      }
    } catch (error) {
      if (error && typeof error === "object" && "errorFields" in error) {
        return;
      }
    } finally {
      setTesting(false);
    }
  };

  const handleSubmit = async () => {
    if (submitting || testing) return;

    try {
      setSubmitting(true);
      const basicValues = await basicForm.validateFields();
      const connectionValues = await configForm.validateFields();
      const payload = buildSubmitPayload(
        selectedDbType,
        basicValues,
        connectionValues,
      );

      const response = isCreateMode
        ? await createDataSource(payload)
        : currentRecord?.id
          ? await updateDataSource(currentRecord.id, payload)
          : undefined;

      if (!response || response.code !== API_SUCCESS_CODE) return;

      const successCallback = successCallbackRef.current;
      message.success(isCreateMode ? "数据源创建成功" : "数据源更新成功");
      setOpen(false);
      successCallback?.();
    } catch (error) {
      if (error && typeof error === "object" && "errorFields" in error) {
        return;
      }
    } finally {
      setSubmitting(false);
    }
  };

  const actionText = isEditMode
    ? intl.formatMessage({
        id: "pages.datasource.modal.title.edit",
        defaultMessage: "编辑",
      })
    : intl.formatMessage({
        id: "pages.datasource.modal.title.add",
        defaultMessage: "新建",
      });

  const drawerTitle = `${actionText}${intl.formatMessage({
    id: "pages.datasource.common.title",
    defaultMessage: "数据源",
  })}`;

  const subtitle = selectedDbType
    ? `${selectedDbType} · ${isEditMode ? "修改连接与基础信息" : "配置连接与基础信息"}`
    : "选择数据源类型后继续配置连接信息";

  const renderFooter = () => {
    if (!showFormStep) {
      return (
        <div className="flex justify-end">
          <Button disabled={busy} onClick={handleClose}>
            取消
          </Button>
        </div>
      );
    }

    return (
      <div className="flex items-center justify-between gap-3">
        <div>
          {isCreateMode && !hideBackButton ? (
            <Button disabled={busy} onClick={handleBackToTypeSelection}>
              上一步
            </Button>
          ) : (
            <Button disabled={busy} onClick={handleClose}>
              取消
            </Button>
          )}
        </div>

        <div className="flex items-center gap-2">
          <Button
            loading={testing}
            disabled={submitting}
            onClick={() => void handleTestConnection()}
          >
            连接测试
          </Button>

          <Button
            type="primary"
            loading={submitting}
            disabled={testing}
            onClick={() => void handleSubmit()}
          >
            {isCreateMode ? "创建数据源" : "保存修改"}
          </Button>
        </div>
      </div>
    );
  };

  return (
    <Drawer
      className="datasource-editor-drawer"
      width={DRAWER_WIDTH}
      placement="right"
      open={open}
      maskClosable={false}
      closable={!busy}
      keyboard={!busy}
      onClose={handleClose}
      afterOpenChange={handleAfterOpenChange}
      destroyOnClose
      styles={{
        header: {
          padding: "15px 20px",
          borderBottom: "1px solid #EEF0F3",
        },
        body: {
          padding: 0,
          overflow: "hidden",
          background: "#FFFFFF",
        },
        footer: {
          padding: "12px 20px",
          borderTop: "1px solid #EEF0F3",
          background: "#FFFFFF",
        },
      }}
      title={
        <div className="flex min-w-0 items-center gap-3 pr-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#EAECF0] bg-[#F7F8FA]">
            <DatabaseIcons dbType={selectedDbType} width="18" height="18" />
          </div>

          <div className="min-w-0">
            <div className="truncate text-[15px] font-semibold leading-6 text-[#161823]">
              {drawerTitle}
            </div>
            {/* <div className="mt-0.5 truncate text-xs font-normal leading-5 text-[#8A8F99]">
              {subtitle}
            </div> */}
          </div>
        </div>
      }
      footer={renderFooter()}
    >
      {showFormStep ? (
        <div className="datasource-editor-drawer__body datasource-editor-drawer__form h-full overflow-y-auto px-5 py-5">
          <DynamicDataSourceForm
            key={`${operateType}-${selectedDbType}-${currentRecord?.id || "create"}`}
            dbType={selectedDbType}
            form={basicForm}
            configForm={configForm}
            operateType={operateType}
            initialConfig={
              isEditMode
                ? parseOriginalJson(currentRecord?.originalJson)
                : undefined
            }
          />
        </div>
      ) : (
        <div className="datasource-editor-drawer__body h-full min-h-0 px-5 py-4">
          <DataSourceTypeSelector
            dataSourceGroups={dataSourceGroupList}
            onSelect={handleSelectDbType}
          />
        </div>
      )}
    </Drawer>
  );
});

AddOrEditDataSourceModal.displayName = "AddOrEditDataSourceModal";

export default AddOrEditDataSourceModal;
