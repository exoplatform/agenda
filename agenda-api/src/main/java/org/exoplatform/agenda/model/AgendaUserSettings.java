package org.exoplatform.agenda.model;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.ws.frameworks.json.impl.*;

import lombok.*;
import lombok.ToString.Exclude;

@Data
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AgendaUserSettings implements Cloneable {

  private String                       agendaDefaultView       = null;

  private String                       agendaWeekStartOn       = null;

  private boolean                      showWorkingTime         = false;

  private String                       workingTimeStart        = null;

  private String                       workingTimeEnd          = null;

  /**
   * @deprecated kept only so that settings blobs written before several
   *             accounts could coexist still parse, and so that a blob written
   *             by this version still reads on an older one. Mirrors the first
   *             entry of {@link #connectedConnectors}; read and mutate the list
   *             instead. Not for removal while unmigrated blobs exist.
   */
  @Deprecated(since = "7.3", forRemoval = false)
  private String                       connectedRemoteProvider = null;

  /**
   * @deprecated same lifecycle as {@link #connectedRemoteProvider}: legacy
   *             mirror of the first connected account's remote user id.
   */
  @Deprecated(since = "7.3", forRemoval = false)
  private String                       connectedRemoteUserId   = null;

  /**
   * Every remote account the user holds, at most one per provider. Replaces
   * the single {@link #connectedRemoteProvider} / {@link #connectedRemoteUserId}
   * pair: a user may now keep one CalDAV account plus one or more remote
   * accounts (Google, Office 365) connected at the same time. Legacy blobs
   * that only carry the two old fields are mapped into this list lazily on
   * read — see {@link #getConnectedConnectors()} — so no migration job runs.
   */
  private List<AgendaConnectorAccount> connectedConnectors     = null;


  @Exclude
  @lombok.EqualsAndHashCode.Exclude
  private String                       cometdToken             = null;

  @Exclude
  @lombok.EqualsAndHashCode.Exclude
  private String                       cometdContextName       = null;

  private boolean                      automaticPushEvents     = true;

  private String                       timeZoneId              = null;

  private List<EventReminderParameter> reminders               = null;

  private List<RemoteProvider>         remoteProviders         = null;

  private List<String>                 webConferenceProviders  = null;

  private String                       workedDaysNumber   = null;

  private boolean                      showRemoteEventsForAgenda         = false;

  private boolean                      showRemoteEventsForTimeLine         = false;

  private String                       embedMapProvider;

  public AgendaUserSettings(String cometdToken,
                            String agendaDefaultView,
                            String agendaWeekStartOn,
                            boolean showWorkingTime,
                            String workingTimeStart,
                            String workingTimeEnd,
                            String connectedRemoteProvider,
                            String connectedRemoteUserId,
                            boolean automaticPushEvents,
                            String timeZoneId,
                            String workedDaysNumber,
                            boolean showRemoteEventsForAgenda,
                            boolean showRemoteEventsForTimeLine) {
    this.cometdToken = cometdToken;
    this.agendaDefaultView = agendaDefaultView;
    this.agendaWeekStartOn = agendaWeekStartOn;
    this.showWorkingTime = showWorkingTime;
    this.workingTimeStart = workingTimeStart;
    this.workingTimeEnd = workingTimeEnd;
    this.connectedRemoteProvider = connectedRemoteProvider;
    this.connectedRemoteUserId = connectedRemoteUserId;
    this.automaticPushEvents = automaticPushEvents;
    this.timeZoneId = timeZoneId;
    this.workedDaysNumber = workedDaysNumber;
    this.showRemoteEventsForAgenda = showRemoteEventsForAgenda;
    this.showRemoteEventsForTimeLine = showRemoteEventsForTimeLine;
  }

  /**
   * Returns every remote account the user holds, lazily mapping the legacy
   * single-connector fields into the list: a blob written before several
   * accounts could coexist carries only {@link #connectedRemoteProvider} and
   * {@link #connectedRemoteUserId}, and its one account materialises here as a
   * one-entry list the first time the list is read. This getter is also what
   * the JSON serialisation reads, so a legacy blob re-saved untouched is
   * written in the new shape (while the legacy fields keep mirroring the first
   * entry for downgrade safety). Never returns null.
   *
   * @return the mutable list of connected accounts, possibly empty
   */
  public List<AgendaConnectorAccount> getConnectedConnectors() {
    if (connectedConnectors == null) {
      connectedConnectors = new ArrayList<>();
    }
    if (connectedConnectors.isEmpty() && StringUtils.isNotBlank(connectedRemoteProvider)) {
      connectedConnectors.add(new AgendaConnectorAccount(connectedRemoteProvider, connectedRemoteUserId, true));
    }
    return connectedConnectors;
  }

  /**
   * Adds the account held on the given provider, or updates its remote user id
   * when the provider already holds one: a user keeps at most one account per
   * provider, so connecting again with another account replaces the previous
   * one rather than accumulating. A newly added account receives copies by
   * default; an updated one keeps the opt-out the user may have chosen.
   *
   * @param providerName name of the remote provider the account is held on
   * @param remoteUserId identifier of the user on the remote provider
   */
  public void addOrUpdateConnectedConnector(String providerName, String remoteUserId) {
    List<AgendaConnectorAccount> accounts = getConnectedConnectors();
    AgendaConnectorAccount existingAccount = accounts.stream()
                                                     .filter(account -> StringUtils.equals(account.getProviderName(),
                                                                                           providerName))
                                                     .findFirst()
                                                     .orElse(null);
    if (existingAccount == null) {
      accounts.add(new AgendaConnectorAccount(providerName, remoteUserId, true));
    } else {
      existingAccount.setRemoteUserId(remoteUserId);
    }
    syncLegacyConnectorFields();
  }

  /**
   * Removes the account held on the given provider, leaving the other
   * connected accounts untouched. A blank provider name removes every account,
   * which is the behaviour the reset endpoint had when only one account could
   * exist.
   *
   * @param providerName name of the remote provider to disconnect, or blank to
   *          disconnect them all
   */
  public void removeConnectedConnector(String providerName) {
    List<AgendaConnectorAccount> accounts = getConnectedConnectors();
    if (StringUtils.isBlank(providerName)) {
      accounts.clear();
    } else {
      accounts.removeIf(account -> StringUtils.equals(account.getProviderName(), providerName));
    }
    syncLegacyConnectorFields();
  }

  /**
   * Keeps the legacy single-connector fields mirroring the first entry of the
   * account list, or null when the list is empty. The mirror is what lets a
   * blob written by this version still read on an older one, and what stops
   * {@link #getConnectedConnectors()} from resurrecting a removed account out
   * of stale legacy fields.
   */
  @SuppressWarnings("deprecation")
  private void syncLegacyConnectorFields() {
    if (connectedConnectors == null || connectedConnectors.isEmpty()) {
      this.connectedRemoteProvider = null;
      this.connectedRemoteUserId = null;
    } else {
      AgendaConnectorAccount firstAccount = connectedConnectors.get(0);
      this.connectedRemoteProvider = firstAccount.getProviderName();
      this.connectedRemoteUserId = firstAccount.getRemoteUserId();
    }
  }

  /**
   * Serialises this object to the JSON blob persisted in the settings store.
   *
   * @return the JSON representation of these settings
   */
  @Override
  public String toString() {
    try {
      return new JsonGeneratorImpl().createJsonObject(this).toString();
    } catch (JsonException e) {
      throw new IllegalStateException("Error parsing current global object to string", e);
    }
  }

  public static AgendaUserSettings fromString(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    try {
      JsonDefaultHandler jsonDefaultHandler = new JsonDefaultHandler();
      new JsonParserImpl().parse(new ByteArrayInputStream(value.getBytes()), jsonDefaultHandler);
      return ObjectBuilder.createObject(AgendaUserSettings.class, jsonDefaultHandler.getJsonObject());
    } catch (JsonException e) {
      throw new IllegalStateException("Error creating object from string : " + value, e);
    }
  }

  /**
   * Clones these settings, deep-copying the connected accounts so that
   * mutating an account on the clone never alters the original — the default
   * settings template is cloned per user, and a shared list would leak one
   * user's accounts into another's defaults.
   *
   * @return an independent copy of these settings
   */
  @Override
  public AgendaUserSettings clone() { // NOSONAR
    AgendaUserSettings clonedSettings = new AgendaUserSettings(cometdToken,
                                                               agendaDefaultView,
                                                               agendaWeekStartOn,
                                                               showWorkingTime,
                                                               workingTimeStart,
                                                               workingTimeEnd,
                                                               connectedRemoteProvider,
                                                               connectedRemoteUserId,
                                                               automaticPushEvents,
                                                               timeZoneId,
                                                               workedDaysNumber,
                                                               showRemoteEventsForAgenda,
                                                               showRemoteEventsForTimeLine);
    if (connectedConnectors != null) {
      clonedSettings.setConnectedConnectors(connectedConnectors.stream()
                                                               .map(AgendaConnectorAccount::clone)
                                                               .collect(Collectors.toList()));
    }
    return clonedSettings;
  }

}
