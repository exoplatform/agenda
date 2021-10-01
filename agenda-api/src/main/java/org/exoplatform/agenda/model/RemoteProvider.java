// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.model;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RemoteProvider {

  private long    id;

  private String  name;

  private String  apiKey;

  private boolean enabled;

}
