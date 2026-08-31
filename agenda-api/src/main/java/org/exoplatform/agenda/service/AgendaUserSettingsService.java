package org.exoplatform.agenda.service;

import java.util.List;

import org.exoplatform.agenda.constant.AvailabilitySharing;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.EventReminderParameter;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaUserSettingsService {

  /**
   * Save user agenda settings to use in events by default
   * 
   * @param identityId technical identifier of {@link Identity}
   * @param agendaUserSettings object of {@link AgendaUserSettings}
   */
  void saveAgendaUserSettings(long identityId, AgendaUserSettings agendaUserSettings);

  /**
   * Get list of user settings to use in events by default
   * 
   * @param identityId technical identifier of {@link Identity}
   * @return {@link AgendaUserSettings}
   */
  AgendaUserSettings getAgendaUserSettings(long identityId);

  /**
   * Saves a connected account on a remote events provider. The account is
   * upserted per provider: a user may hold several accounts at the same time
   * (typically one CalDAV account plus one or more remote accounts), at most
   * one per provider, so connecting a provider that already holds an account
   * replaces that account's remote user id and leaves the other providers'
   * accounts untouched.
   *
   * @param connectorName connector identifier
   * @param connectorUserId user identifier on remote provider
   * @param userIdentityId user social identifier
   */
  void saveUserConnector(String connectorName, String connectorUserId, long userIdentityId);

  /**
   * Removes a connected account from the user's agenda settings, leaving the
   * accounts held on other providers untouched. A blank connector name removes
   * every connected account, which is the behaviour the reset had when only
   * one account could exist.
   *
   * @param connectorName connector identifier of the account to remove, or
   *          blank to remove them all
   * @param userIdentityId user social identifier
   */
  void removeUserConnector(String connectorName, long userIdentityId);

  /**
   * @return {@link List} of {@link EventReminderParameter} that will be used
   *         for users who didn't changed default settings about preferred
   *         reminders
   */
  List<EventReminderParameter> getDefaultReminders();


  /**
   * Update the user TimeZONE
   *
   * @param userName  userName
   * @param timeZone  timeZone
   * @throws ObjectNotFoundException when user profile is not found
   */
  void updateUserTimeZone(String userName, String timeZone) throws ObjectNotFoundException;

  /**
   * Retrieves the globally configured embed map provider identifier.
   * This setting is shared across all users of the platform.
   *
   * @return the provider id (e.g. {@code "google-maps"} or {@code "openStreet-map"}),
   *         or {@code null} if no provider has been explicitly configured
   */
  String getEmbedMapProvider();

  /**
   * Saves the globally configured embed map provider identifier.
   * This setting is shared across all users of the platform.
   *
   * @param providerId the identifier of the map provider to use, must not be blank
   * @throws IllegalArgumentException if {@code providerId} is blank
   */
  void saveEmbedMapProvider(String providerId);

  /**
   * Removes the globally configured embed map provider setting.
   * After this call, {@link #getEmbedMapProvider()} will return {@code null}.
   */
  void removeEmbedMapProvider();

  /**
   * Returns how widely the given user lets their busy time be disclosed to
   * other people.
   * <p>
   * A user who has never chosen gets {@link AvailabilitySharing#DEFAULT}. A
   * stored value that cannot be understood is treated as
   * {@link AvailabilitySharing#NOBODY}: absence means "never chose", which the
   * default answers, whereas an unreadable value is a broken store, and a
   * broken store must not widen a disclosure.
   * <p>
   * This is the only reader of that stored value. What it means for who may
   * read whose free/busy is decided in exactly one other place,
   * {@link AgendaAvailabilityService} — this method reports a preference, it
   * does not grant anything.
   *
   * @param userIdentityId technical identifier of the user whose choice is
   *          wanted
   * @return the user's choice, never {@code null}
   */
  AvailabilitySharing getAvailabilitySharing(long userIdentityId);

  /**
   * Records how widely a user lets their busy time be disclosed to other
   * people.
   * <p>
   * The value is stored under its own settings key rather than inside the
   * {@link AgendaUserSettings} blob, because that blob is replaced wholesale
   * by {@code PUT /v1/agenda/settings} with whatever the client sends: a
   * client unaware of the field would silently reset a user's disclosure
   * choice, which is not a thing a settings save may do by omission.
   * <p>
   * Only the user concerned may change their own choice; the caller is
   * responsible for having established that the identity it passes is the
   * authenticated one, as for every other per-user setting here.
   *
   * @param userIdentityId technical identifier of the user making the choice
   * @param availabilitySharing the choice to record
   * @throws IllegalArgumentException when the identity is not a positive
   *           identifier, or the choice is {@code null}
   */
  void saveAvailabilitySharing(long userIdentityId, AvailabilitySharing availabilitySharing);
}
