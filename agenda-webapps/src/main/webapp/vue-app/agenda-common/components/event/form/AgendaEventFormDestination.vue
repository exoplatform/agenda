/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see gnu.org/licenses.
 */
<template>
  <!--
    Space-only contexts (a space agenda portlet, or a timeline configured on
    selected spaces) keep the historical space suggester untouched: this
    wrapper only exists to reintroduce a personal destination in the personal
    agenda, where the stock form offered none.
  -->
  <agenda-event-form-calendar-owner
    v-if="spaceOnlyMode"
    ref="calendarOwner"
    :event="event"
    :current-space="currentSpace"
    :calendars="calendars"
    @initialized="$emit('initialized')" />
  <div
    v-else
    class="d-flex flex-column">
    <!--
      An outlined dense v-select, the platform idiom for a bordered select
      (email-connector contact phone type): unlike a native select it renders
      the per-calendar color dots and the divider before the space entry, and
      carries the same border as the fields around it instead of the
      browser's own focus ring.

      In the expanded form (inline) each destination line mirrors the title
      input's my-3 margins and event-input width, so select and suggester
      read as two more fields of the form's single column — same 40px box,
      same width, same vertical rhythm. The drawer keeps its historical
      mt-3 / mt-2 spacing and full drawer width.
    -->
    <div
      :class="inline ? 'my-3 event-input' : 'mt-3'"
      class="d-flex flex-row align-center">
      <v-select
        v-model="selectedValue"
        :items="destinationItems"
        :aria-label="$t('agenda.destination.label')"
        :menu-props="{bottom: true, offsetY: true}"
        class="agenda-event-form-destination flex-grow-1 pt-0 mt-0"
        outlined
        dense
        hide-details>
        <!--
          Three things share the row and each owns its space, so the row's
          shape is the same whatever the calendar is called: the colour dot,
          which never gives up its 12px; the name, which is the only part
          that gives way and ellipsises inside what is left; and the dropdown
          arrow, which keeps its own box at the end. Before this the name was
          a text run with the dot inline in it and nothing bounding it, and
          it was the field's height that absorbed a long one (see the skin
          rules on .agenda-event-form-destination).

          The whole name is on the row's title, as a consequence rather than
          as the point. A calendar keeps the name its owner gave it — a CalDAV
          server publishes 'Stalwart Calendar (alice@stalwart.local)' and eXo
          stores that verbatim — so what a clip takes is the part naming the
          account, and two connected accounts give two calendars diverging
          only there. The hover is the treatment EXO-89825 and EXO-89840
          already settled on for exactly that.

          The same markup in both slots on purpose: the row the menu offers
          and the row the closed select shows are the same row, and a name
          that reads one way open and another way closed is one more thing to
          keep in step.
        -->
        <template #item="{ item }">
          <div class="agenda-destination-option d-flex align-center">
            <v-icon
              v-if="item.color"
              :color="item.color"
              size="12"
              class="me-2 flex-shrink-0">
              fa-circle
            </v-icon>
            <span :title="item.text" class="text-truncate">{{ item.text }}</span>
          </div>
        </template>
        <template #selection="{ item }">
          <div class="agenda-destination-option d-flex align-center">
            <v-icon
              v-if="item.color"
              :color="item.color"
              size="12"
              class="me-2 flex-shrink-0">
              fa-circle
            </v-icon>
            <span :title="item.text" class="text-truncate">{{ item.text }}</span>
          </div>
        </template>
      </v-select>
    </div>
    <agenda-event-form-calendar-owner
      v-if="spacesModeSelected"
      ref="calendarOwner"
      :event="event"
      :current-space="currentSpace"
      :calendars="calendars"
      :class="inline ? 'event-input' : 'mt-2'"
      @initialized="$emit('initialized')" />
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    currentSpace: {
      type: Object,
      default: () => null,
    },
    calendars: {
      type: Array,
      default: () => [],
    },
    inline: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    personalCalendars: [],
    // The calendars a colleague shared with this user for editing
    // (EXO-90378): a second group of the destination select, because an
    // editor creates in the owner's calendar from their own agenda
    editableShares: [],
    selectedValue: null,
    initialized: false,
    // Whom this component last proposed as the only attendee (EXO-90378), so
    // that changing the destination again replaces exactly that proposal and
    // never a colleague the user themselves added
    proposedAttendeeId: null,
  }),
  computed: {
    /**
     * Whether the destination is restricted to spaces: inside a space agenda
     * the space is fixed, and a timeline configured on selected spaces keeps
     * its space-only gating. Both keep the historical behavior byte for
     * byte.
     *
     * @returns {Boolean} true when only space destinations are offered
     */
    spaceOnlyMode() {
      return !!this.currentSpace
        || !!(this.$root.timelineSettings && this.$root.timelineSettings.agendaSource === 'selectedSpaces');
    },
    /**
     * Whether the user chose to file the event in a space, which swaps the
     * historical space suggester in on its own line below the select.
     *
     * @returns {Boolean} true when the space flow is selected
     */
    spacesModeSelected() {
      return this.selectedValue === 'spaces';
    },
    /**
     * The items of the destination select: the user's personal calendars
     * (color dot + name), then a divider, then the entry leading to the
     * space flow.
     *
     * @returns {Array} select items
     */
    destinationItems() {
      const items = this.personalCalendars.map(calendar => ({
        text: this.calendarLabel(calendar),
        value: `calendar-${calendar.id}`,
        color: calendar.color,
      }));
      // Then the calendars shared with this user for editing (EXO-90378),
      // under a header of their own so the owner's name is never mistaken for
      // one of the user's own calendars
      if (this.editableShares.length) {
        items.push({header: this.$t('agenda.destination.sharedEditable')});
        this.editableShares.forEach(share => items.push({
          text: share.name,
          value: `calendar-${share.calendarId}`,
          color: share.color,
        }));
      }
      items.push({divider: true});
      items.push({
        text: this.$t('agenda.destination.spaces'),
        value: 'spaces',
      });
      return items;
    },
    /**
     * The identity id of the current user.
     *
     * @returns {Number} current user identity id
     */
    userIdentityId() {
      return Number(eXo.env.portal.userIdentityId);
    },
  },
  watch: {
    /**
     * Applies the picked destination to the event payload. A personal
     * calendar writes the explicit calendar id (what the server-side
     * resolution consumes) plus the user owner block; the space entry clears
     * both so the space suggester drives the payload exactly as before.
     * @returns {void}
     */
    selectedValue() {
      if (!this.initialized || this.selectedValue === null) {
        return;
      }
      if (!this.event.calendar) {
        this.$set(this.event, 'calendar', {});
      }
      if (this.spacesModeSelected) {
        // Only clear the payload when leaving a personal destination: when a
        // space event is being edited, its owner block is what the space
        // suggester preselects from and must be preserved
        const owner = this.event.calendar.owner;
        const ownerIsUser = owner && (String(owner.id) === String(this.userIdentityId)
          || owner.providerId === 'organization');
        if (!owner || ownerIsUser) {
          this.$set(this.event.calendar, 'id', 0);
          this.$set(this.event.calendar, 'owner', null);
          this.$root.$emit('agenda-event-change-owner', null);
        }
      } else {
        const calendarId = Number(String(this.selectedValue).replace('calendar-', ''));
        // A calendar shared with this user for editing belongs to its owner,
        // not to them (EXO-90378): the owner block must name that colleague,
        // which is what the server checks the calendar row against
        const share = this.editableShareOf(calendarId);
        this.proposeDefaultAttendee(share);
        this.$set(this.event.calendar, 'id', calendarId);
        this.$set(this.event.calendar, 'owner', share ? {
          id: String(share.ownerId),
          providerId: 'organization',
          remoteId: share.ownerUsername,
        } : {
          id: String(this.userIdentityId),
          providerId: 'organization',
          remoteId: eXo.env.portal.userName,
        });
        this.$root.$emit('agenda-event-change-owner', null);
      }
    },
  },
  created() {
    if (!this.spaceOnlyMode) {
      this.retrievePersonalCalendars();
    }
  },
  methods: {
    /**
     * Retrieves the user's personal calendars then preselects the event's
     * current destination: the event's calendar when editing a personal
     * event, the space flow when editing a space event, the system default
     * calendar ('My calendar') for a new event — creating a personal event
     * stays zero extra clicks.
     *
     * @returns {Promise} resolved once the selection is initialized
     */
    retrievePersonalCalendars() {
      return this.$calendarService.getCalendars(0, 100, false, [this.userIdentityId])
        .then(data => {
          this.personalCalendars = data && data.calendars || [];
          this.personalCalendars.sort((calendar1, calendar2) => (calendar2.system - calendar1.system)
            || this.calendarLabel(calendar1).localeCompare(this.calendarLabel(calendar2)));
          return this.retrieveEditableShares();
        })
        .then(() => this.initializeSelection())
        .finally(() => {
          this.initialized = true;
          this.$emit('initialized');
        });
    },
    /**
     * The calendars a colleague shared with this user for editing
     * (EXO-90378), hidden ones left out: hiding a calendar takes it out of
     * the agenda, so it has no business being a destination either. A
     * failure leaves the group empty — the user's own calendars are what the
     * form cannot do without.
     *
     * @returns {Promise} resolved once read
     */
    retrieveEditableShares() {
      if (!this.$calendarShareService) {
        return Promise.resolve();
      }
      return this.$calendarShareService.getSharedWithMe()
        .then(shares => {
          this.editableShares = (shares || []).filter(share => share.access === 'EDIT' && !share.hidden);
          this.editableShares.sort((first, second) => String(first.name || '').localeCompare(String(second.name || '')));
        })
        .catch(() => this.editableShares = []);
    },
    /**
     * Proposes whom a new event is with, when the destination changes
     * (EXO-90378): the calendar's owner for a calendar shared with this user
     * for editing — an event an editor files in a colleague's calendar is a
     * meeting with that colleague, and they would otherwise have to be added
     * by hand every time — and the signed-in user again for one of their own.
     * <p>
     * Only a proposal, and only ever over another proposal: it replaces the
     * lone attendee this component itself put there, or an empty list, and
     * never a list the user has touched. Never on an event being edited.
     *
     * @param {Object} share the share the destination names, null for one of
     *        the user's own calendars
     * @returns {void}
     */
    proposeDefaultAttendee(share) {
      if (this.event.id || this.event.occurrence) {
        return;
      }
      const attendees = this.event.attendees || [];
      const only = attendees.length === 1 && attendees[0].identity && attendees[0].identity.id;
      const replaceable = attendees.length === 0
        || String(only) === String(this.proposedAttendeeId)
        || String(only) === String(this.userIdentityId);
      if (!replaceable) {
        return;
      }
      if (share) {
        this.$set(this.event, 'attendees', [{identity: {
          id: String(share.ownerId),
          providerId: 'organization',
          remoteId: share.ownerUsername,
          profile: {fullname: share.ownerDisplayName, avatar: share.ownerAvatarUrl},
        }}]);
        this.proposedAttendeeId = String(share.ownerId);
      } else if (this.proposedAttendeeId) {
        this.$set(this.event, 'attendees', []);
        this.proposedAttendeeId = null;
      }
    },
    /**
     * The share a destination names, when it names one shared with this user
     * for editing (EXO-90378).
     *
     * @param {Number} calendarId technical identifier of the calendar
     * @returns {Object} the share, or null
     */
    editableShareOf(calendarId) {
      return this.editableShares.find(share => Number(share.calendarId) === Number(calendarId)) || null;
    },
    /**
     * Computes the initial selection from the event being created or edited.
     * @returns {void}
     */
    initializeSelection() {
      // Applying the initial selection also writes it to the payload (the
      // watcher below), so the save button validity reflects it immediately
      this.initialized = true;
      const storedOwner = this.event && this.event.calendar && this.event.calendar.owner;
      // New events ship a bare `owner: {}` placeholder: only an owner
      // carrying an identity counts as a stored destination, anything else
      // must fall through to the default personal calendar below
      const owner = storedOwner
        && (storedOwner.id || storedOwner.providerId || storedOwner.remoteId) ? storedOwner : null;
      const ownerIsUser = owner && (String(owner.id) === String(this.userIdentityId)
        || owner.providerId === 'organization' && owner.remoteId === eXo.env.portal.userName);
      if (owner && !ownerIsUser && this.event.calendar.id && this.editableShareOf(this.event.calendar.id)) {
        // Editing an event of a calendar a colleague shared with this user for
        // editing (EXO-90378): asked before the space branch, since its owner
        // is not this user either. It stays where it is filed — an editor may
        // not move an event out, and this select would be the only way to try.
        this.selectedValue = `calendar-${Number(this.event.calendar.id)}`;
      } else if (owner && !ownerIsUser) {
        // Editing (or pre-filling) an event belonging to a space
        this.selectedValue = 'spaces';
      } else if (ownerIsUser && this.event.calendar.id
          && this.personalCalendars.some(calendar => Number(calendar.id) === Number(this.event.calendar.id))) {
        // Editing a personal event: keep it where it is filed
        this.selectedValue = `calendar-${Number(this.event.calendar.id)}`;
      } else {
        // New event: the system (default, undeletable) personal calendar —
        // not merely the first row, which drifts for multi-calendar users
        const defaultCalendar = this.personalCalendars.find(calendar => calendar.system) || this.personalCalendars[0];
        this.selectedValue = defaultCalendar ? `calendar-${defaultCalendar.id}` : 'spaces';
      }
    },
    /**
     * The display label of a personal calendar: its name, else the localized
     * 'My calendar' for the unnamed default.
     *
     * @param {Object} calendar the calendar to label
     * @returns {String} display label
     */
    calendarLabel(calendar) {
      if (calendar.name) {
        return calendar.name;
      }
      return calendar.system ? this.$t('agenda.myCalendar') : (calendar.title || this.$t('agenda.myCalendar'));
    },
    /**
     * Clears any custom validity set by the space suggester. A personal
     * destination has nothing to clear.
     * @returns {void}
     */
    resetCustomValidity() {
      if (this.$refs.calendarOwner) {
        this.$refs.calendarOwner.resetCustomValidity();
      }
    },
    /**
     * Validates the destination: a picked personal calendar is always valid;
     * the space flow delegates to the historical suggester validation, which
     * requires a space to be chosen.
     * @returns {void}
     */
    validateForm() {
      if (this.$refs.calendarOwner) {
        this.$refs.calendarOwner.validateForm();
      }
    },
  },
};
</script>
