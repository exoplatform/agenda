package org.exoplatform.agenda.model;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RemoteProvider {

  private long    id;

  private String  name;

  private String  apiKey;

  private String  secretKey;

  private boolean enabled;

  private Boolean oauth;

}
