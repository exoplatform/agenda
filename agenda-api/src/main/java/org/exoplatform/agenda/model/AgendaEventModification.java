package org.exoplatform.agenda.model;

import java.util.HashSet;
import java.util.Set;

import org.exoplatform.agenda.constant.AgendaEventModificationType;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgendaEventModification {

  private long                             eventId;

  private long                             modifierId;

  private Set<AgendaEventModificationType> modificationTypes;

  public AgendaEventModification(long eventId, long modifierId) {
    this.eventId = eventId;
    this.modifierId = modifierId;
  }

  public void addModificationType(AgendaEventModificationType modificationType) {
    if (modificationTypes == null) {
      modificationTypes = new HashSet<>();
    }
    modificationTypes.add(modificationType);
  }

  public void removeModification(AgendaEventModificationType modificationType) {
    if (modificationTypes != null) {
      modificationTypes.remove(modificationType);
    }
  }

  public boolean hasModification(AgendaEventModificationType modificationType) {
    return modificationTypes != null && modificationTypes.contains(modificationType);
  }

  public void addModificationTypes(Set<AgendaEventModificationType> eventModifications) {
    if (eventModifications != null && !eventModifications.isEmpty()) {
      modificationTypes.addAll(eventModifications);
    }
  }
}
