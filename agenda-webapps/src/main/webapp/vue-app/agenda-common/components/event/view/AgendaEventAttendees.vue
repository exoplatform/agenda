<template>
  <div class="d-flex align-center full-width">
    <v-icon size="20" class="icon-default-color me-2 flex-shrink-0">fas fa-users</v-icon>
    <div class="d-flex align-center ms-6 flex-grow-1 attendee-status-badges">
      <v-badge
        v-if="acceptedResponsesCount"
        :content="acceptedResponsesCount"
        :value="acceptedResponsesCount"
        offset-x="13"
        color="warning-color-background"
        offset-y="12"
        class="me-8">
        <v-btn
          icon
          small
          :title="$t('agenda.filter.button.title.accepted')"
          @click="openDrawer('ACCEPTED')">
          <v-icon size="20" class="success-color">fas fa-check-circle</v-icon>
        </v-btn>
      </v-badge>
      <v-badge
        v-if="tentativeResponsesCount"
        :content="tentativeResponsesCount"
        :value="tentativeResponsesCount"
        color="warning-color-background"
        offset-x="13"
        offset-y="12"
        class="me-8">
        <v-btn
          icon
          small
          :title="$t('agenda.filter.button.title.tentative')"
          @click="openDrawer('TENTATIVE')">
          <v-icon size="20" class="primary--text">fas fa-question-circle</v-icon>
        </v-btn>
      </v-badge>
      <v-badge
        v-if="refusedResponsesCount"
        :content="refusedResponsesCount"
        :value="refusedResponsesCount"
        color="warning-color-background"
        offset-x="13"
        offset-y="12"
        class="me-8">
        <v-btn
          icon
          small
          :title="$t('agenda.filter.button.title.declined')"
          @click="openDrawer('DECLINED')">
          <v-icon size="20" class="error-color">fas fa-times-circle</v-icon>
        </v-btn>
      </v-badge>
      <v-badge
        v-if="waitingResponsesCount"
        :content="waitingResponsesCount"
        :value="waitingResponsesCount"
        color="warning-color-background"
        offset-x="13"
        offset-y="12"
        class="me-8">
        <v-btn
          icon
          small
          :title="$t('agenda.filter.button.title.waiting')"
          @click="openDrawer('WAITING')">
          <v-icon size="20" class="info--text">fas fa-info-circle</v-icon>
        </v-btn>
      </v-badge>
    </div>
    <agenda-event-attendees-avatars
      v-if="displayedAttendees.length"
      :attendees="displayedAttendees"
      :max="3"
      :size="32"
      @open="openDrawer" />
    <agenda-event-form-attendees-drawer
      ref="attendeesDrawer"
      :event="event"
      :editable="canEdit"
      :exportable="canExport"
      :export-disabled="listUnsaved"
      @export="exportAttendees"
      @toggle-open="toggleOpen"
      @change-capacity="changeCapacity"
      @closed="saveDrawerChangesIfEditable" />
    <agenda-capacity-close-confirm-dialog ref="closeConfirm" />
    <!--
      The scope popup of the event form, reused as it stands: a change made from
      the participants drawer of one date of a series asks the same question,
      with the same three answers, as a change made from the form (eXIP
      7.3.0.20, US06). Kept local rather than routed through the global
      agenda-event-save bus, which closes the event page on save — from here the
      organiser stays on the event they were reading.

      changed-fields names what the pending question is about, and it matters:
      without it, "all events" and "this and upcoming" promote the displayed
      date's whole state onto the series — its summary, its location and its
      time of day, which that date may have moved on its own. One participant
      added would move a weekly meeting for everyone. It is set per gesture, not
      once for the component: a question about the participant list must not
      carry that date's padlock to the series.
    -->
    <agenda-recurrent-event-save-confirm-dialog
      ref="scopeDialog"
      :changed-fields="scopeFields"
      :attendees-delta="scopeAttendeesDelta"
      @save-event="applyScopedChange"
      @dialog-closed="scopeDialogClosed" />
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => null
    },
  },
  computed: {
    canEdit() {
      return !!(this.event && this.event.acl && this.event.acl.canEdit);
    },
    /**
     * The export of the participant list (eXIP 7.3.0.21, US05): for whoever
     * may edit a confirmed event, and absent from a date poll. The server
     * checks the same right again.
     *
     * @returns {Boolean} whether the drawer offers the export
     */
    canExport() {
      return this.canEdit && this.event.status === 'CONFIRMED';
    },
    /**
     * The file is built from the stored list, and the drawer saves its changes
     * when it closes: while this visit holds unsaved additions or removals, the
     * export waits, so that the file is always the list the drawer shows.
     *
     * @returns {Boolean} whether the drawer changed the participant list since
     *          it was opened, and has not saved it yet
     */
    listUnsaved() {
      return this.attendeesSnapshot !== null && this.attendeesChanged();
    },
    /**
     * @returns {Boolean} whether the page displays one date of a series rather
     *          than a standalone event or the series itself
     */
    isOccurrence() {
      return !!(this.event && this.event.occurrence && this.event.occurrence.id && this.event.parent);
    },

    attendees() {
      return this.event && this.event.attendees || [];
    },
    creatorAttendee() {
      if (!this.event || !this.attendees || !this.attendees.length) {
        return null;
      }
      return this.attendees.find(attendee => attendee.identity.id === this.event.creator.id);
    },
    creatorAttendeeResponse() {
      return this.creatorAttendee && this.creatorAttendee.response;
    },
    participatingAttendees() {
      if (!this.creatorAttendee) {
        return this.attendees;
      }
      return this.attendees.filter(attendee => attendee.identity.id !== this.creatorAttendee.identity.id);
    },
    participatingUserAttendees() {
      return this.participatingAttendees.filter(attendee => attendee.identity.profile);
    },
    participatingSpaceAttendees() {
      return this.participatingAttendees
        .filter(attendee => attendee.identity.space)
        .sort(this.sortAttendees);
    },
    acceptedResponses() {
      return this.participatingUserAttendees
        .filter(attendee => attendee && attendee.response === 'ACCEPTED')
        .sort(this.sortAttendees);
    },
    tentativeResponses() {
      return this.participatingUserAttendees
        .filter(attendee => attendee && attendee.response === 'TENTATIVE')
        .sort(this.sortAttendees);
    },
    refusedResponses() {
      return this.participatingUserAttendees
        .filter(attendee => attendee && attendee.response === 'DECLINED')
        .sort(this.sortAttendees);
    },
    needsActionResponses() {
      return this.participatingUserAttendees
        .filter(attendee => attendee && attendee.response === 'NEEDS_ACTION')
        .sort(this.sortAttendees);
    },
    waitingResponses() {
      return this.participatingUserAttendees.filter(attendee => attendee && attendee.response === 'WAITING');
    },
    acceptedResponsesCount() {
      return this.acceptedResponses.length + (this.creatorAttendeeResponse === 'ACCEPTED' && 1 || 0);
    },
    tentativeResponsesCount() {
      return this.tentativeResponses.length + (this.creatorAttendeeResponse === 'TENTATIVE' && 1 || 0);
    },
    refusedResponsesCount() {
      return this.refusedResponses.length + (this.creatorAttendeeResponse === 'DECLINED' && 1 || 0);
    },
    needsActionResponsesCount() {
      return this.needsActionResponses.length + (this.creatorAttendeeResponse === 'NEEDS_ACTION' && 1 || 0);
    },
    // Shown to everyone who sees the event, as the other counts are
    waitingResponsesCount() {
      return this.waitingResponses.length + (this.creatorAttendeeResponse === 'WAITING' && 1 || 0);
    },
    displayedAttendees() {
      const others = this.participatingAttendees.slice().sort(this.sortAttendees);
      return [this.creatorAttendee, ...others].filter(Boolean);
    },
    visibleIdentities() {
      return this.displayedAttendees.slice(0, 3).map(a => {
        const identity = a.identity;
        const profile = identity.profile || identity.space || {};
        return {
          ...identity,
          username: identity.remoteId,
          fullname: profile.fullname || profile.fullName || profile.displayName || identity.remoteId,
          avatar: profile.avatar || profile.avatarUrl,
        };
      });
    },
  },
  data() {
    return {
      attendeesSnapshot: null,
      attendeesBackup: null,
      openBackup: false,
      togglingOpen: false,
      scopedChangePending: false,
      // What the pending question is about. Set per gesture rather than fixed,
      // because a wider scope writes exactly these fields onto the series: a
      // participant added to a date of a locked series that someone had opened
      // on its own would otherwise carry that date's padlock to the whole
      // series, and from there to every other date
      scopeFields: [],
      // And what it did to the participant list, as a delta: a wider scope
      // applies it to a list that is not the one the drawer edited
      scopeAttendeesDelta: null,
    };
  },
  methods: {
    /**
     * Opens or locks the event from the detail page.
     *
     * On a standalone event, or on a series displayed as itself, there is
     * nothing to ask and the flag is patched on the spot; the page is updated
     * only once the server accepted.
     *
     * On one date of a series the click only marks the drawer, exactly as
     * adding a participant does: since US06 the flag is a property of each
     * date, so it needs the same scope question — and asking it here, while the
     * organiser is still editing, would ask twice for one visit. The question
     * is raised once, when the drawer closes, over everything that changed.
     *
     * @returns {void}
     */
    toggleOpen() {
      if (!this.event || this.togglingOpen || this.scopedChangePending) {
        return;
      }
      const open = !this.event.open;
      if (this.isOccurrence) {
        this.$set(this.event, 'open', open);
        if (open) {
          this.clearInvitePermission();
        }
        return;
      }
      const eventId = this.event.id;
      if (!eventId) {
        return;
      }
      // Locking an open event with a limit removes it and confirms its waiting
      // list: asked first (D15). A limit the stepper shows before the drawer
      // closes counts too, since the closing removes it as well
      if (!open && this.hasLimit()) {
        this.$refs.closeConfirm.confirm()
          .then(() => this.patchOpen(eventId, open, true))
          .catch(() => null);
        return;
      }
      this.patchOpen(eventId, open, false);
    },
    /**
     * @param {Number} eventId the event patched, standalone or a series
     * @param {Boolean} open the padlock's new state
     * @param {Boolean} confirmClose whether the organiser confirmed the closing
     * @returns {void}
     */
    patchOpen(eventId, open, confirmClose) {
      const hadLimit = this.hasLimit();
      this.togglingOpen = true;
      // updateAllOccurrences is true so that a series patched from its own page
      // reaches the dates individually modified as well: since US06 such a
      // patch merges into those dates instead of deleting them, leaving
      // untouched only the properties customised there
      // Opening clears "participants can invite" in the same patch: an open
      // event excludes it, the forms never show it (it follows "can modify"
      // through a watcher), and an event whose modify option was switched on
      // and off keeps it on — the server accepts the two fields together and
      // would refuse the opening alone
      const fields = open ? {open, allowAttendeeToInvite: false} : {open};
      const write = confirmed => this.$eventService.updateEventFields({id: eventId}, fields, true, false, confirmed);
      this.$refs.closeConfirm.writeConfirmingClose(write, confirmClose)
        .then(() => {
          this.$set(this.event, 'open', open);
          if (open) {
            this.clearInvitePermission();
          }
          // Stored, so it is the drawer's new reference point. Without this the
          // close would read a changed padlock and full-save the event a second
          // time — and since the wire never carries sendInvitation (the WS JSON
          // provider drops transient fields), EventService defaults it to true:
          // an "event modified" notification to every attendee, for one click
          this.openBackup = open;
          // A closing removed the limit and confirmed the waiting list
          if (!open && hadLimit) {
            return this.refreshDisplayedEvent();
          }
        })
        .catch(this.changeFailed)
        .finally(() => this.togglingOpen = false);
    },
    /**
     * The limit is applied with the rest of the drawer's changes, when it
     * closes, as the participant list is.
     *
     * @param {Number} capacity the limit chosen, 0 for none
     * @returns {void}
     */
    changeCapacity(capacity) {
      this.$set(this.event, 'capacity', capacity);
    },
    /**
     * @returns {Boolean} whether the event has a limit, as stored or as the
     *          drawer now shows it
     */
    hasLimit() {
      return this.capacityBackup > 0 || this.event.capacity > 0;
    },
    openDrawer(responseFilter) {
      // The drawer edits the event in place — the list and the padlock alike —
      // so the state to come back to, when the organiser cancels the scope
      // popup or when the save fails, has to be kept before anything touches it
      this.rememberDrawerState();
      this.$refs.attendeesDrawer.open(responseFilter);
    },
    /**
     * Downloads the participant list as stored, which is the list the drawer
     * shows: the export waits while the visit holds unsaved changes (see
     * listUnsaved).
     *
     * @returns {void}
     */
    exportAttendees() {
      // A date of a series that has no row of its own is its series, at that date
      const computedOccurrence = !this.event.id && this.event.parent && this.event.occurrence;
      const eventId = computedOccurrence ? this.event.parent.id : this.event.id;
      const occurrenceId = computedOccurrence ? this.event.occurrence.id : null;
      this.$eventService.exportEventAttendees(eventId, occurrenceId, this.$agendaUtils.USER_TIMEZONE_ID);
    },
    /**
     * Takes the event as the reference point for what follows: what "changed"
     * is measured against it, and what a cancel or a failed save puts back is a
     * copy of it.
     *
     * @returns {void}
     */
    rememberDrawerState() {
      const attendees = this.event && this.event.attendees || [];
      this.attendeesSnapshot = attendees.map(a => a.identity.remoteId).sort().join(',');
      this.attendeesBackup = JSON.parse(JSON.stringify(attendees));
      this.openBackup = !!(this.event && this.event.open);
      this.capacityBackup = this.event && this.event.capacity || 0;
    },
    /**
     * @returns {Boolean} whether the drawer changed the limit since it was
     *          opened
     */
    capacityChanged() {
      return (this.event && this.event.capacity || 0) !== this.capacityBackup;
    },
    /**
     * @returns {Boolean} whether the drawer changed the padlock since it was
     *          opened
     */
    openChanged() {
      return !!(this.event && this.event.open) !== this.openBackup;
    },
    /**
     * @returns {Boolean} whether the drawer changed the participant list since
     *          it was opened
     */
    attendeesChanged() {
      const current = (this.event && this.event.attendees || [])
        .map(a => a.identity.remoteId).sort().join(',');
      return current !== this.attendeesSnapshot;
    },
    /**
     * What the drawer did to the list, as a delta rather than the list itself.
     * A wider scope has to apply it to the series' own list, which is not this
     * one: someone removed from this date alone is still on the series, and
     * handing over this date's list would uninvite them from the whole meeting
     * and, through the merge, from every other date.
     *
     * People are named by their participant key rather than their identity id:
     * someone the organiser just picked in the suggester has no id yet — the
     * server assigns it when the event is saved — so an id-keyed delta would
     * not recognise the addition it exists to carry.
     *
     * @returns {Object} the attendees added and the participant keys removed
     *          since the drawer was opened
     */
    attendeesDelta() {
      return this.$agendaUtils.attendeesDelta(this.attendeesBackup, this.event && this.event.attendees);
    },
    saveDrawerChangesIfEditable() {
      if (!this.canEdit || !this.event) {
        return;
      }
      if (this.scopedChangePending) {
        // Defence in depth: a question is already waiting for an answer, and a
        // second one here would replace the event it holds. No path raises one
        // while the drawer is open today — the padlock only marks the drawer —
        // so this guards a coupling, not a case that occurs
        return;
      }
      const listChanged = this.attendeesChanged();
      const padlockChanged = this.openChanged();
      const capacityChanged = this.capacityChanged();
      if (!listChanged && !padlockChanged && !capacityChanged) {
        return;
      }
      if (this.isOccurrence) {
        // One question for the whole visit, over everything the drawer changed.
        // The padlock travels as a named field; the list travels as a delta, so
        // that a wider scope adds and removes the same people on the series'
        // own list instead of replacing it with this date's — someone removed
        // from this date alone is still on the series. Invitations follow an
        // invitation, not a padlock.
        const eventToSave = JSON.parse(JSON.stringify(this.event));
        eventToSave.sendInvitation = listChanged;
        // Opening travels with the invite permission it clears (see toggleOpen)
        const fields = padlockChanged ? (this.event.open ? ['open', 'allowAttendeeToInvite'] : ['open']) : [];
        if (capacityChanged) {
          fields.push('capacity');
        }
        const delta = listChanged ? this.attendeesDelta() : null;
        // Locking a date with a limit removes it and confirms its waiting
        // list: asked first (D15), whichever scope comes next
        if (padlockChanged && !this.event.open && this.capacityBackup > 0) {
          this.$refs.closeConfirm.confirm()
            .then(() => this.askScope(eventToSave, fields, delta, true))
            .catch(() => this.restoreDrawerState());
          return;
        }
        this.askScope(eventToSave, fields, delta, false);
        return;
      }
      // A standalone event, or a series displayed as itself: nothing to ask.
      // A changed list is a full save, which carries the limit too. A limit
      // changed alone is patched, as the padlock is (see patchOpen): a full
      // save would tell every attendee the event changed. A new limit may
      // have given seats, read back
      const write = listChanged
        ? confirmed => this.$eventService.updateEvent(this.event, confirmed)
        : confirmed => this.$eventService.updateEventFields({id: this.event.id}, {capacity: this.event.capacity || 0}, true, false, confirmed);
      this.$refs.closeConfirm.writeConfirmingClose(write, false)
        .then(() => capacityChanged && this.refreshDisplayedEvent())
        .catch(error => {
          this.restoreDrawerState();
          this.changeFailed(error);
        });
    },
    /**
     * @param {Object} eventToSave the event as the organiser left it — the
     *          padlock, the participant list, or both — cloned so that nothing
     *          reaches the page before the server accepted it
     * @param {Array} changedFields the fields this question is about, which a
     *          wider scope writes onto the series and nothing else. The
     *          participant list is never one of them — it travels as a delta,
     *          because a wider scope applies it to a list this drawer never saw
     * @param {Object} attendeesDelta what the drawer added to and removed from
     *          the list, or null when it changed nobody
     * @param {Boolean} confirmClose whether the organiser confirmed that the
     *          change closes the date with its limit
     * @returns {void}
     */
    askScope(eventToSave, changedFields, attendeesDelta, confirmClose) {
      this.scopedChangePending = true;
      this.scopeConfirmClose = !!confirmClose;
      this.scopeFields = changedFields;
      this.scopeAttendeesDelta = attendeesDelta || null;
      this.$refs.scopeDialog.open(eventToSave, false, null, confirmClose);
    },
    /**
     * Applies the answer to "only this event" and to "all events". "This and
     * upcoming events" normally does its own work — it shortens the series,
     * creates the second one and announces the result, which closes the event
     * page — and reaches here only when the displayed date is the first of the
     * series, where the popup rightly treats it as "all events".
     *
     * A date the series had never materialised carries no id, so the change
     * creates its row; every other case updates one.
     *
     * @param {Object} eventToSave the event the popup built for the chosen
     *          scope — that date alone, or the series carrying it
     * @returns {void}
     */
    applyScopedChange(eventToSave) {
      const write = confirmed => (eventToSave.id
        ? this.$eventService.updateEvent(eventToSave, confirmed)
        : this.$eventService.createEvent(eventToSave));
      this.$refs.closeConfirm.writeConfirmingClose(write, this.scopeConfirmClose)
        .then(() => {
          // The change landed, so the list the drawer edited is the stored one
          // now and closing the popup must not put the previous one back
          this.scopedChangePending = false;
          this.$refs.scopeDialog.close();
          this.$root.$emit('agenda-refresh');
          return this.refreshDisplayedEvent();
        })
        .catch(error => {
          // Closing puts the previous participant list back, through
          // scopeDialogClosed
          this.$refs.scopeDialog.close();
          this.changeFailed(error);
        });
    },
    /**
     * Reads back what the server stored, whichever scope was chosen: the date
     * displayed may have gained a row of its own, and its padlock and its
     * participants may now come from itself rather than from the series — the
     * page has no way of deducing which. Best effort: the change is already
     * saved, so a failed read is not a failed save and says nothing.
     *
     * @returns {Promise} resolved once the page shows the stored state, or once
     *          the read has failed
     */
    refreshDisplayedEvent() {
      const reloadPromise = this.event.occurrence && this.event.occurrence.id
        ? this.$eventService.getEventOccurrence(this.event.parent.id, this.event.occurrence.id, 'all,parentAll')
        : this.$eventService.getEventById(this.event.id, 'all,parentAll');
      return reloadPromise
        .then(storedEvent => {
          if (!storedEvent) {
            return;
          }
          this.$set(this.event, 'open', storedEvent.open);
          this.$set(this.event, 'capacity', storedEvent.capacity);
          this.$set(this.event, 'acceptedCount', storedEvent.acceptedCount);
          this.$set(this.event, 'attendees', storedEvent.attendees || []);
          if (this.event.parent && storedEvent.parent) {
            this.$set(this.event.parent, 'open', storedEvent.parent.open);
          }
          // The popup is closed before this read returns, so an organiser who
          // reopens the drawer in that window snapshots the pre-save event; when
          // the read lands and replaces attendees and open, the next close would
          // see a difference and ask the same question again. Re-taking the
          // reference point on what the server stored closes that race
          this.rememberDrawerState();
        })
        .catch(() => this.$root.$emit('agenda-refresh'));
    },
    /**
     * The popup closed without the organiser choosing a scope: whatever the
     * drawer changed in place goes back to what it was.
     *
     * @returns {void}
     */
    scopeDialogClosed() {
      if (!this.scopedChangePending) {
        return;
      }
      this.scopedChangePending = false;
      this.restoreDrawerState();
    },
    restoreDrawerState() {
      if (this.event && this.attendeesBackup) {
        this.$set(this.event, 'attendees', JSON.parse(JSON.stringify(this.attendeesBackup)));
        this.$set(this.event, 'open', this.openBackup);
        this.$set(this.event, 'capacity', this.capacityBackup);
      }
    },
    /**
     * The server refuses with a message code (a stale page: the event was moved
     * to a personal calendar or turned into a date poll meanwhile); shown when
     * the bundle knows it, the generic text otherwise.
     *
     * @param {Object} error the rejected response
     * @returns {void}
     */
    /**
     * An open event excludes "participants can invite", which the forms never
     * show: cleared on the page as the server clears it, so that the drawer's
     * next close compares like with like.
     *
     * @returns {void}
     */
    clearInvitePermission() {
      this.$set(this.event, 'allowAttendeeToInvite', false);
    },
    changeFailed(error) {
      if (!error) {
        // The organiser cancelled a closing: nothing was written
        return;
      }
      const code = error.code;
      let message = this.$t('agenda.openEvent.updateError');
      if (code && error.date && this.$te(`${code}OnDate`)) {
        // A limit of a series below the Yes answers of one of its dates says
        // which one (D16)
        message = this.$t(`${code}OnDate`, {0: this.$agendaUtils.formatDay(error.date)});
      } else if (code && this.$te(code)) {
        message = this.$t(code);
      }
      this.$root.$emit('alert-message', message, 'error');
    },
    sortAttendees(attendee1, attendee2) {
      const displayName1 = (attendee1.identity.profile && attendee1.identity.profile.fullname)
        || (attendee1.identity.space && attendee1.identity.space.displayName) || '';
      const displayName2 = (attendee2.identity.profile && attendee2.identity.profile.fullname)
        || (attendee2.identity.space && attendee2.identity.space.displayName) || '';
      return displayName1.localeCompare(displayName2);
    },
  },
};
</script>