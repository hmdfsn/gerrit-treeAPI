// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.extensions.webui;

import com.google.gerrit.extensions.common.WebLinkInfo;

public interface FileHistoryWebLink extends WebLink {
  /**
   * {@link com.google.gerrit.extensions.common.WebLinkInfo} describing a link from a file to an
   * external service displaying a log for that file.
   *
   * <p>In order for the web link to be visible {@link
   * com.google.gerrit.extensions.common.WebLinkInfo#url} and {@link
   * com.google.gerrit.extensions.common.WebLinkInfo#name} must be set.
   *
   * <p>
   *
   * @param projectName Name of the project
   * @param revision Name of the revision (e.g. branch or commit ID)
   * @param fileName Name of the file
   * @return WebLinkInfo that links to a log for the file in external service, null if there should
   *     be no link.
   */
  WebLinkInfo getFileHistoryWebLink(String projectName, String revision, String fileName);
}
