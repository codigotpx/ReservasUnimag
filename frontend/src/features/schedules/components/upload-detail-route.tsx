"use client";

import { useParams } from "next/navigation";
import { UploadDetail } from "./upload-detail";

export function UploadDetailRoute() {
  const { uploadId } = useParams<{ uploadId: string }>();
  return <UploadDetail uploadId={uploadId} />;
}
