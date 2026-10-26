<template>
  <div class="d-flex flex-column full-width">
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
          color="info-color-background"
          offset-x="13"
          offset-y="12"
          class="me-8">
          <v-btn
            icon
            small
            :title="$t('agenda.filter.button.title.waiting')"
            @click="openDrawer('WAITING')">
            <v-icon size="20" class="warning--text">fas fa-info-circle</v-icon>
          </v-btn>
        </v-badge>
      </div>
      <agenda-event-attendees-avatars
        v-if="displayedAttendees.length"
        :attendees="displayedAttendees"
        :max="3"
        :size="32"
        @open="openDrawer" />
    </div>
    <div v-if="capacityIndicator" class="d-flex align-center mt-3">
      <v-icon size="18" class="warning--text me-2 flex-shrink-0">{{ showsWaitingPosition ? 'fas fa-info-circle' : 'fas fa-user-lock' }}</v-icon>
      <span class="text-color">{{ capacityIndicator }}</span>
    </div>
    <agenda-event-form-attendees-drawer
      ref="attendeesDrawer"
      :event="event"
      :editable="canEdit"
      :exportable="canExport"
      :export-disabled="listUnsaved"
      @export="exportAttendees"
      @toggle-open="toggleOpen"
      @change-capacity="changeCapacity"
      @seats-given="removalGaveSeats"
      @closed="drawerClosed" />
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
      :saving="scopeWriting"
      :before-page-close="closeDrawerBeforePageClose"
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
     * The limit of an event that has one, shown with its participants to
     * whoever sees them; a viewer on the waiting list reads their place in
     * the line instead
     *
     * @returns {String} the line, empty without a limit
     */
    capacityIndicator() {
      const capacity = this.event && this.event.capacity || 0;
      if (!capacity) {
        return '';
      }
      if (this.showsWaitingPosition) {
        return this.$t('agenda.capacity.waitingPosition', {0: this.event.waitingListPosition});
      }
      return capacity === 1 ? this.$t('agenda.capacity.indicatorOne') : this.$t('agenda.capacity.indicator', {0: capacity});
    },
    showsWaitingPosition() {
      return this.viewerResponse === 'WAITING' && !!(this.event && this.event.waitingListPosition);
    },
    viewerResponse() {
      const viewer = this.event && this.event.attendees && this.event.attendees.find(attendee => attendee.identity && Number(attendee.identity.id) === Number(eXo.env.portal.userIdentityId));
      return viewer && viewer.response || null;
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
     * export waits. On an event or a series displayed as itself, a reopen
     * also waits for the save its close started and for the read of the
     * answers that follows it (see openDrawer).
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
      // The save a close started, while it runs: a reopen waits for it
      drawerSave: null,
      // The reads of the stored event issued, the latest one landing alone
      readCount: 0,
      openBackup: false,
      // Whether the organiser confirmed, at the padlock, that this visit closes
      // a date of a series with its limit
      closeConfirmed: false,
      togglingOpen: false,
      // The padlock's change while it runs: the answer to its closing warning,
      // then, on an event of its own, its write with the read a closing makes.
      // A close of the drawer meanwhile saves once it ended (see drawerClosed)
      openWrite: null,
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
      // A seat given elsewhere that no read issued since covers
      liveReadPending: false,
      // The reads of the stored event on their way
      readsInFlight: 0,
      // Whether the drawer is open, between its opening and its close
      drawerVisit: false,
      // Whether a seat was given elsewhere since the page last replaced the
      // event it shows
      seatGivenSinceShown: false,
      // Whether the drawer's changes wait to be saved once the write on its
      // way, or the question it asks, has ended (see settleQueuedSave)
      drawerSaveQueued: false,
      // What the drawer held when the pending scope question was asked
      scopeCarried: null,
      // Whether the answer to that question is being written: the popup
      // stays open and answers nothing more meanwhile (see applyScopedChange)
      scopeWriting: false,
      // How to undo each change that gave a seat on a date, made since the
      // last scope question was asked, and those that question carries: a
      // cancel undoes those alone (see scopeDialogClosed)
      seatChanges: [],
      scopeSeatChanges: [],
    };
  },
  watch: {
    /**
     * The page replaces the event it shows with a read of its own once the
     * viewer answered, a read no ordering ties to this section's: issued
     * before a seat given elsewhere and served after, it would show the
     * event without that seat. Read again when a seat was given meanwhile.
     *
     * @param {Object} event the event now shown
     * @param {Object} previousEvent the event shown before
     * @returns {void}
     */
    event(event, previousEvent) {
      if (event && previousEvent && this.seatGivenSinceShown && this.eventKey(event) === this.eventKey(previousEvent)) {
        this.readSeatsLive();
      }
      this.seatGivenSinceShown = false;
    },
  },
  created() {
    this.pageClosed = false;
    this.$root.$on('agenda-event-live-update', this.readSeatGivenElsewhere);
  },
  beforeDestroy() {
    this.$root.$off('agenda-event-live-update', this.readSeatGivenElsewhere);
    // Read by what waited for the padlock's change, answered below
    this.pageClosed = true;
    if (this.openWrite) {
      // A warning the padlock's change waits for goes with the page (Escape
      // closes the event's page): nobody can answer it any more, so it is
      // answered no and the drawer's close waiting for it goes on (see
      // drawerClosed). One asked by another write stays unanswered: what
      // follows its answer needs the page
      this.$refs.closeConfirm.settle(false);
    }
  },
  methods: {
    /**
     * A seat given from the waiting list, whoever freed it, shows on the page
     * of the event without a refresh (EXO-91879): to the person promoted, as
     * their answer and the limit line, and to the organiser, in the
     * participant list and in the drawer when it is open. The message names
     * the event row or its series, so a date of the series reads itself again.
     *
     * The seat is marked to read and read when nothing of the page's own is
     * on its way (see settleLiveRead): seats given one after the other, as
     * when a limit is lifted, cost one read more, not one each.
     *
     * @param {String} wsEventName the name of the event the server sent
     * @param {Number} eventId the event or the series it names
     * @param {Number} calendarId the calendar of that event
     * @returns {void}
     */
    readSeatGivenElsewhere(wsEventName, eventId, calendarId) {
      if (wsEventName !== 'exo.agenda.event.attendee.promoted' || !this.displaysEvent(eventId, calendarId)) {
        return;
      }
      this.seatGivenSinceShown = true;
      this.readSeatsLive();
    },
    readSeatsLive() {
      this.liveReadPending = true;
      this.settleLiveRead();
    },
    /**
     * Reads a seat given elsewhere once nothing of the page's own is on its
     * way: an editor's drawer open with changes, which a read would replace,
     * until it closes; a write, whose own read follows, until it ends; a
     * read, which may predate the seat, until it lands.
     *
     * @returns {void}
     */
    settleLiveRead() {
      if (!this.liveReadPending || this.readsInFlight || this.pageChanging()) {
        return;
      }
      this.refreshDisplayedEvent(true);
    },
    /**
     * @returns {Boolean} whether the page is changing the event: an editor's
     *          drawer open with changes, or a write on its way. A read of a
     *          seat given elsewhere would replace those changes, issued or
     *          landing meanwhile
     */
    pageChanging() {
      return !!this.drawerSave || this.scopedChangePending || this.togglingOpen || this.drawerHoldsChanges();
    },
    /**
     * @param {Number} eventId an event or a series
     * @param {Number} calendarId the calendar of that event
     * @returns {Boolean} whether the page displays that event, or a date of
     *          that series. A date with no row of its own gets one when it is
     *          first answered, under an id the page does not know until it
     *          reads the date again: for such a date, the calendar decides
     */
    displaysEvent(eventId, calendarId) {
      const id = Number(eventId);
      if (!this.event || !id) {
        return false;
      }
      if (Number(this.event.id) === id || this.event.parent && Number(this.event.parent.id) === id) {
        return true;
      }
      return !Number(this.event.id) && !!this.event.parent
        && !!calendarId && Number(this.event.calendar && this.event.calendar.id) === Number(calendarId);
    },
    /**
     * @param {Object} event an event the page shows
     * @returns {String} what identifies it across the page's reads: a date
     *          of a series by its series and its date, since its row id
     *          appears once the date is first answered. An all-day date by
     *          its day only: computed on a zone's skipped midnight, it and the
     *          row it gets differ by the hour, which the server ignores too. A
     *          timed date keeps its instant, as its row does
     */
    eventKey(event) {
      if (!event.parent || !event.occurrence || !event.occurrence.id) {
        return `${event.id}`;
      }
      const date = String(event.occurrence.id);
      return `${event.parent.id}@${event.allDay ? date.slice(0, 10) : date}`;
    },
    /**
     * @returns {Boolean} whether the drawer is open on changes of an editor's
     *          to the list, the padlock or the limit. A viewer who cannot edit
     *          changes nothing there, and once the drawer is closed, its
     *          reference point no longer means anything to measure against
     */
    drawerHoldsChanges() {
      return this.drawerVisit && this.canEdit && this.attendeesSnapshot !== null
        && (this.attendeesChanged() || this.openChanged() || this.capacityChanged());
    },
    /**
     * Opens or locks the event from the detail page.
     *
     * On a standalone event, or on a series displayed as itself, there is
     * nothing to ask and the flag is patched on the spot; the page is updated
     * only once the server accepted.
     *
     * On one date of a series the click marks the drawer, exactly as
     * adding a participant does: since US06 the flag is a property of each
     * date, so it needs the same scope question — and asking it here, while the
     * organiser is still editing, would ask twice for one visit. The question
     * is raised over everything that changed, when the drawer closes or when a
     * removal or the date closed gives a seat (see saveDrawerNow).
     *
     * The warning of a closing with a limit is not that question: it is asked
     * at the click on both paths, so that a cancel leaves the drawer open on
     * the event as it was.
     *
     * @returns {void}
     */
    toggleOpen() {
      // A save of the drawer on its way carries the padlock as it was
      if (!this.event || this.writePending()) {
        return;
      }
      if (!this.isOccurrence && !this.event.id) {
        return;
      }
      const open = !this.event.open;
      // Locking an open event with a limit removes it and confirms its waiting
      // list: asked first (D15). A limit the stepper shows before the drawer
      // closes counts too, since the closing removes it as well
      if (!open && this.hasLimit()) {
        // The drawer's close button can close it behind the warning: that
        // close waits for the answer, and on a standalone event for the write
        // the answer starts, as for a padlock's write (see drawerClosed)
        const pending = this.$refs.closeConfirm.confirm()
          .then(() => {
            this.applyOpen(open, true);
            return this.isOccurrence ? null : this.openWrite;
          })
          .catch(() => null)
          .finally(() => {
            if (this.openWrite === pending) {
              this.openWrite = null;
            }
            this.settleQueuedSave();
          });
        this.openWrite = pending;
        return;
      }
      this.applyOpen(open, false);
    },
    /**
     * @param {Boolean} open the padlock's new state
     * @param {Boolean} confirmClose whether the organiser confirmed the closing
     * @returns {void}
     */
    applyOpen(open, confirmClose) {
      if (!this.isOccurrence) {
        this.patchOpen(this.event.id, open, confirmClose);
        return;
      }
      const seatedBefore = this.$refs.attendeesDrawer.seatsGivenByChanges;
      this.$set(this.event, 'open', open);
      // Carried by the scope question the drawer's close asks
      this.closeConfirmed = confirmClose;
      if (open) {
        this.clearInvitePermission();
      } else if (this.$refs.attendeesDrawer.givesAnotherSeat(seatedBefore)) {
        // A date closed seats its waiting list: asked how far it reaches and
        // saved at once, as a removal that gives a seat (EXO-91879)
        const event = this.event;
        this.seatChanges.push(() => {
          this.$set(event, 'open', true);
          this.closeConfirmed = false;
        });
        this.saveDrawerNow();
      }
    },
    /**
     * @param {Object} removal the participant the drawer removed, giving a
     *          seat, and where it was on the list
     * @returns {void}
     */
    removalGaveSeats(removal) {
      if (this.isOccurrence && removal) {
        // Put back where it was, as stored, unless added again since: a read
        // landed since may have seated them, or no longer list them
        const event = this.event;
        const key = this.$agendaUtils.participantKey(removal.attendee);
        this.seatChanges.push(() => {
          const attendees = event.attendees || [];
          const stored = (this.attendeesBackup || []).find(attendee => this.$agendaUtils.participantKey(attendee) === key);
          if (stored && !attendees.some(attendee => this.$agendaUtils.participantKey(attendee) === key)) {
            attendees.splice(Math.min(removal.index, attendees.length), 0, JSON.parse(JSON.stringify(stored)));
          }
        });
      }
      this.saveDrawerNow();
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
      const patchedEvent = this.event;
      this.openWrite = this.$refs.closeConfirm.writeConfirmingClose(write, confirmClose)
        .then(() => {
          if (this.eventKey(this.event) !== this.eventKey(patchedEvent)) {
            // The page shows another event by now, whose padlock this is not
            return;
          }
          this.$set(this.event, 'open', open);
          if (open) {
            this.clearInvitePermission();
          } else {
            // A closing removes the limit (D15), whether the read below
            // lands or not: the drawer's close has none left to save
            this.$set(this.event, 'capacity', 0);
            this.capacityBackup = 0;
          }
          // Stored, so it is the drawer's new reference point. Without this the
          // close would read a changed padlock and full-save the event a second
          // time — and since the wire never carries sendInvitation (the WS JSON
          // provider drops transient fields), EventService defaults it to true:
          // an "event modified" notification to every attendee, for one click
          this.openBackup = open;
          // A closing removed the limit and confirmed the waiting list
          if (!open && hadLimit) {
            return this.refreshDisplayedEvent(false, true);
          }
        })
        .catch(this.changeFailed)
        .finally(() => {
          this.togglingOpen = false;
          this.openWrite = null;
          this.settleQueuedSave();
          this.settleLiveRead();
        });
    },
    /**
     * The limit is applied with the rest of the drawer's changes, when it
     * closes, as the participant list is. On an event of its own, a limit
     * raised so that it shows another person from the waiting list seated is
     * saved at once, for that person to get the seat at the same time
     * (EXO-91879). Not on a date of a series, where each step of the stepper
     * would ask how far the change reaches: there it goes with the next change
     * saved. Nor when the limit is removed, which seats the whole waiting list
     * and cannot be taken back once saved: the "−" at the floor removes it, so
     * one click would.
     *
     * @param {Number} capacity the limit chosen, 0 for none
     * @returns {void}
     */
    changeCapacity(capacity) {
      const drawer = this.$refs.attendeesDrawer;
      const seatedBefore = drawer.seatsGivenByChanges;
      this.$set(this.event, 'capacity', capacity);
      if (!this.isOccurrence && capacity > 0 && drawer.givesAnotherSeat(seatedBefore)) {
        this.saveDrawerNow();
      }
    },
    /**
     * Saves what the drawer holds while it stays open, once a change there
     * shows another person from the waiting list seated (EXO-91879): a
     * removal, the date closed, and the limit on an event of its own (see
     * changeCapacity). The person the drawer shows seated gets it on the
     * server at the same time, rather than when the drawer closes. On a date
     * of a series, the scope question is asked then, above the drawer.
     *
     * One write at a time: a change made while one is on its way, or while
     * the question waits for an answer, is saved once it ended, with what
     * the drawer holds then.
     *
     * @returns {void}
     */
    saveDrawerNow() {
      if (!this.drawerVisit) {
        return;
      }
      if (this.writePending()) {
        this.queueDrawerSave();
        return;
      }
      this.saveDrawerChangesIfEditable();
    },
    queueDrawerSave() {
      this.drawerSaveQueued = true;
      // The event the drawer edits: the page may show another by then
      this.queuedEvent = this.event;
    },
    /**
     * Saves the drawer's changes that waited for a write or a question to
     * end (see saveDrawerNow and drawerClosed), measured against the event as
     * that write left it. The scope question of a date leaves with its page.
     *
     * @returns {void}
     */
    settleQueuedSave() {
      if (!this.drawerSaveQueued || this.writePending()) {
        return;
      }
      this.drawerSaveQueued = false;
      const closeConfirm = this.$refs.closeConfirm || this.closeConfirmAtClose;
      // The scope question the changes of a date need left with the page:
      // the date they were made on, which the page may not show any more
      if (!closeConfirm || this.pageClosed && this.isDateOfSeries(this.queuedEvent)) {
        return;
      }
      if (this.eventKey(this.event) !== this.eventKey(this.queuedEvent)) {
        this.saveChangesLeft(this.queuedEvent, this.drawerChangesSince({
          event: this.queuedEvent,
          attendees: this.attendeesBackup || [],
          capacity: this.capacityBackup || 0,
        }));
        return;
      }
      this.saveDrawerChangesIfEditable(closeConfirm);
    },
    /**
     * Saves on the event the page left the drawer's changes still waiting
     * for a write ("View" on the message of an answer shows another event in
     * the same page): on an event of its own, or a series shown as itself,
     * as the padlock's wait does (see drawerClosed). A date of a series needs
     * the scope question, which cannot be asked once the page shows another
     * event: its changes are given up.
     *
     * @param {Object} event the event the drawer changed
     * @param {Object} changes what it changed there (see drawerChangesSince)
     * @returns {void}
     */
    saveChangesLeft(event, changes) {
      this.drawerSaveQueued = false;
      if (!this.isDateOfSeries(event)) {
        this.saveChangesOfEventLeft(event.id, changes.attendees, changes.capacity, this.$refs.closeConfirm || this.closeConfirmAtClose);
      }
    },
    /**
     * @param {Object} event an event the page shows, or showed
     * @returns {Boolean} whether it is one date of a series, whose changes
     *          ask how far they reach
     */
    isDateOfSeries(event) {
      return !!(event && event.occurrence && event.occurrence.id && event.parent);
    },
    /**
     * @returns {Boolean} whether a write of this section is on its way, or a
     *          question about one waits for an answer
     */
    writePending() {
      return !!this.drawerSave || this.scopedChangePending || this.togglingOpen || !!this.openWrite;
    },
    /**
     * @returns {Boolean} whether the event has a limit, as stored or as the
     *          drawer now shows it
     */
    hasLimit() {
      return this.capacityBackup > 0 || this.event.capacity > 0;
    },
    openDrawer(responseFilter) {
      // A reopen waits for the save the last close started, and for the read
      // of the answers that follows it: the state taken below, which the
      // export compares with and a failed save puts back, is then not the
      // changes still on their way. So is the save that close queued behind
      // one on its way, which starts once that one ended, on a date with its
      // scope question. A page closed meanwhile has no drawer left to open
      const saved = () => {
        if (this.drawerSave) {
          return this.drawerSave.then(saved);
        }
        return this.scopedChangePending ? this.scopeQuestion.then(saved) : Promise.resolve();
      };
      saved().then(() => {
        if (this.pageClosed) {
          return;
        }
        // The drawer edits the event in place — the list and the padlock alike —
        // so the state to come back to, when the organiser cancels the scope
        // popup or when the save fails, has to be kept before anything touches it
        this.rememberDrawerState();
        this.seatChanges = [];
        this.drawerVisit = true;
        this.$refs.attendeesDrawer.open(responseFilter);
      });
    },
    /**
     * The drawer's changes are saved, for whoever may edit, and a seat given
     * elsewhere during the visit is read once nothing of the page's own is on
     * its way.
     *
     * A padlock's write still on its way is waited for, with the read a
     * closing makes: a save started before would carry the padlock and the
     * limit as they were before it (EXO-90310). So is the answer to the
     * closing warning, which the padlock's change waits for. A reopen waits
     * for that save as well. So is a save the drawer started while open, and
     * the scope question it asked (see saveDrawerNow).
     *
     * @returns {void}
     */
    drawerClosed() {
      this.drawerVisit = false;
      if (this.drawerSave || this.scopedChangePending) {
        // Saved once it ended, through the dialog the page has now: a page
        // closed meanwhile has none left by then
        this.closeConfirmAtClose = this.$refs.closeConfirm;
        this.queueDrawerSave();
        return;
      }
      if (this.openWrite) {
        // Kept as a delta, applied once the wait ended to the event shown
        // then: the page replaces it with a read of its own once the
        // organiser answered. And saved through the dialog the page has now:
        // a page closed meanwhile has none left by then
        const editedEvent = this.event;
        const unsavedList = this.attendeesChanged() ? this.attendeesDelta() : null;
        const unsavedCapacity = this.capacityChanged() ? this.event.capacity || 0 : null;
        const closeConfirm = this.$refs.closeConfirm;
        // Saved below with the rest of the visit, a change that waited for
        // the padlock's write included (see saveDrawerNow)
        this.drawerSaveQueued = false;
        this.drawerSave = this.openWrite.then(() => {
          this.drawerSave = null;
          if (this.pageClosed && this.isOccurrence) {
            // The scope question a date's changes need left with the page
            return null;
          }
          if (this.eventKey(this.event) !== this.eventKey(editedEvent)) {
            this.settleLiveRead();
            return this.saveChangesOfEventLeft(editedEvent.id, unsavedList, unsavedCapacity, closeConfirm);
          }
          if (unsavedList) {
            this.$set(this.event, 'attendees', this.$agendaUtils.withAttendeesDelta(this.event.attendees, unsavedList));
          }
          // A closing removed the limit, unless it failed
          if (unsavedCapacity !== null && this.event.open) {
            this.$set(this.event, 'capacity', unsavedCapacity);
          }
          this.saveDrawerChangesIfEditable(closeConfirm);
          this.settleLiveRead();
          return this.drawerSave;
        });
        return;
      }
      this.saveDrawerChangesIfEditable();
      this.settleLiveRead();
    },
    /**
     * Saves what the drawer changed on an event the page left while the
     * padlock's write ran, read again as that write left it: the people added
     * and removed, and the limit while the event is still open, the write
     * having failed. The padlock itself is stored, or its failure was told; a
     * closing removes the limit, and an opening shows the limit once stored,
     * after the drawer was left.
     *
     * As the drawer's own close does, a limit changed alone is patched: a
     * full save would tell every attendee the event changed.
     *
     * @param {Number} eventId the event the drawer changed, on its own or a
     *          series shown as itself
     * @param {Object} attendeesDelta what the drawer did to its list, or null
     * @param {Number} capacity the limit the drawer set, or null
     * @param {Object} closeConfirm the dialog that asks to confirm a closing
     * @returns {Promise} resolved once saved, or once a failure was told
     */
    saveChangesOfEventLeft(eventId, attendeesDelta, capacity, closeConfirm) {
      if (!attendeesDelta && capacity === null) {
        return Promise.resolve();
      }
      return this.$eventService.getEventById(eventId, 'all,parentAll')
        .then(storedEvent => {
          const capacityKept = capacity !== null && storedEvent.open;
          if (!attendeesDelta && !capacityKept) {
            return;
          }
          const eventToSave = {
            ...this.eventWithDatesToSave(storedEvent),
            attendees: this.$agendaUtils.withAttendeesDelta(storedEvent.attendees, attendeesDelta),
            capacity: capacityKept ? capacity : storedEvent.capacity,
          };
          const write = attendeesDelta
            ? confirmed => this.$eventService.updateEvent(eventToSave, confirmed)
            : confirmed => this.$eventService.updateEventFields({id: eventId}, {capacity}, true, false, confirmed);
          return closeConfirm.writeConfirmingClose(write, false);
        })
        .catch(this.changeFailed);
    },
    /**
     * Downloads the participant list as stored; the export waits while the
     * drawer holds unsaved changes (see listUnsaved).
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
      this.closeConfirmed = false;
      // And the drawer's, for the seats its changes would give
      if (this.$refs.attendeesDrawer) {
        this.$refs.attendeesDrawer.rememberSeats();
      }
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
    /**
     * @param {Object} event an event as the server returned it
     * @returns {Object} a copy to save, its dates written as the forms write
     *          them: an all-day day sent as read is taken for midnight UTC,
     *          the day before west of UTC (see AgendaUtils#toDate)
     */
    eventWithDatesToSave(event) {
      return {
        ...event,
        start: this.$agendaUtils.toRFC3339(this.$agendaUtils.toDate(event.start)),
        end: this.$agendaUtils.toRFC3339(this.$agendaUtils.toDate(event.end)),
      };
    },
    /**
     * @param {Object} closeConfirm the dialog that asks to confirm a closing,
     *          the page's own unless given (see drawerClosed)
     * @returns {void}
     */
    saveDrawerChangesIfEditable(closeConfirm = this.$refs.closeConfirm) {
      if (!this.canEdit || !this.event) {
        return;
      }
      if (this.scopedChangePending) {
        // Defence in depth: a question is already waiting for an answer, and a
        // second one here would replace the event it holds. No path asks one
        // while another waits — the drawer's own saves wait for its answer
        // (see saveDrawerNow), and the padlock asks at most the closing
        // warning, which is not this question — so this guards a coupling,
        // not a case that occurs
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
        const eventToSave = JSON.parse(JSON.stringify(this.eventWithDatesToSave(this.event)));
        eventToSave.sendInvitation = listChanged;
        // Opening travels with the invite permission it clears (see toggleOpen)
        const fields = padlockChanged ? (this.event.open ? ['open', 'allowAttendeeToInvite'] : ['open']) : [];
        if (capacityChanged) {
          fields.push('capacity');
        }
        const delta = listChanged ? this.attendeesDelta() : null;
        // Locking a date with a limit removes it and confirms its waiting
        // list (D15): confirmed at the padlock (see toggleOpen) and carried to
        // whichever scope comes next
        this.askScope(eventToSave, fields, delta, padlockChanged && !this.event.open && this.closeConfirmed);
        return;
      }
      // A standalone event, or a series displayed as itself: nothing to ask.
      // A changed list is a full save, which carries the limit too. A limit
      // changed alone is patched, as the padlock is (see patchOpen): a full
      // save would tell every attendee the event changed. Either one may give
      // seats on the server — a new limit, or a Yes removed from the list —
      // so the answers are read back, as the drawer and the export show them
      const write = listChanged
        ? confirmed => this.$eventService.updateEvent(this.eventWithDatesToSave(this.event), confirmed)
        : confirmed => this.$eventService.updateEventFields({id: this.event.id}, {capacity: this.event.capacity || 0}, true, false, confirmed);
      const carried = this.drawerContent();
      this.drawerSave = closeConfirm.writeConfirmingClose(write, false)
        .then(() => {
          this.takeAsStored(carried);
          return this.refreshDisplayedEvent(false, false, carried);
        })
        .catch(error => {
          // Everything the drawer changed since it was saved last is given
          // up, put back on the event it was made on, which the page may
          // have left by now
          if (this.eventKey(this.event) === this.eventKey(carried.event)) {
            this.restoreDrawerState();
          } else {
            this.drawerSaveQueued = false;
          }
          this.changeFailed(error);
        })
        .finally(() => {
          this.drawerSave = null;
          this.settleQueuedSave();
          this.settleLiveRead();
        });
    },
    /**
     * @returns {Object} what the drawer holds, as a write carries it: the
     *          participant list, copied, the limit and the padlock, with the
     *          event it edits them on
     */
    drawerContent() {
      return {
        event: this.event,
        attendees: JSON.parse(JSON.stringify(this.event.attendees || [])),
        capacity: this.event.capacity || 0,
        open: !!this.event.open,
      };
    },
    /**
     * @param {Object} carried what the drawer held when a write took it (see
     *          drawerContent)
     * @returns {Object} what the drawer changed since, measured on the event
     *          it edited, which the page may have replaced since: the people
     *          added and removed, null when nobody, and the limit, null when
     *          the same
     */
    drawerChangesSince(carried) {
      const event = carried.event;
      const delta = this.$agendaUtils.attendeesDelta(carried.attendees, event.attendees);
      const capacity = event.capacity || 0;
      return {
        attendees: delta.added.length || delta.removedParticipantKeys.length ? delta : null,
        capacity: capacity !== carried.capacity ? capacity : null,
      };
    },
    /**
     * Takes what a write carried as the reference point once it is stored,
     * before the read that follows lands: should that read fail, the drawer
     * is measured against what is stored, and a failed save puts that back,
     * rather than what the drawer was opened on.
     *
     * @param {Object} carried what the write carried (see drawerContent)
     * @returns {void}
     */
    takeAsStored(carried) {
      this.attendeesSnapshot = carried.attendees.map(a => a.identity.remoteId).sort().join(',');
      this.attendeesBackup = JSON.parse(JSON.stringify(carried.attendees));
      this.capacityBackup = carried.capacity;
      this.openBackup = carried.open;
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
      // Ended once answered and written, or cancelled (see openDrawer)
      this.scopeQuestion = new Promise(resolve => this.scopeQuestionEnded = resolve);
      this.scopeCarried = this.drawerContent();
      this.scopeConfirmClose = !!confirmClose;
      this.scopeFields = changedFields;
      this.scopeAttendeesDelta = attendeesDelta || null;
      this.scopeSeatChanges = this.seatChanges;
      this.seatChanges = [];
      this.$refs.scopeDialog.open(eventToSave, false, null, confirmClose);
    },
    /**
     * "This and upcoming events" ends by closing the event page (see
     * applyScopedChange). Asked during a visit (see saveDrawerNow), it would
     * close the page with the drawer open. An exo-drawer destroyed open
     * leaves its panel on screen, stays among the opened drawers and keeps
     * the drawers' mask, since it cleans up only when it closes. So the
     * drawer is closed first. A change made in it after the question was
     * asked is not saved: the page that would ask its scope is closing.
     *
     * @returns {Promise} resolved once the drawer's close has run
     */
    closeDrawerBeforePageClose() {
      if (this.drawerVisit) {
        this.$refs.attendeesDrawer.close();
      }
      return this.$nextTick();
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
      // The popup answers nothing more, and cannot be dismissed, until the
      // write ended (see AgendaRecurrentEventSaveConfirmDialog#busy): a
      // second write, or the drawer put back while this one lands, would
      // undo what the page shows
      this.scopeWriting = true;
      const write = confirmed => (eventToSave.id
        ? this.$eventService.updateEvent(eventToSave, confirmed)
        : this.$eventService.createEvent(eventToSave));
      this.$refs.closeConfirm.writeConfirmingClose(write, this.scopeConfirmClose)
        .then(() => {
          // The change landed, so the list the drawer edited is the stored one
          // now and closing the popup must not put the previous one back
          this.scopeWriting = false;
          this.scopedChangePending = false;
          this.$refs.scopeDialog.close();
          this.$root.$emit('agenda-refresh');
          this.takeAsStored(this.scopeCarried);
          // The read is part of the save, as on an event of its own: the
          // padlock and the drawer's next save wait for it, which would
          // otherwise act on what it replaces
          this.drawerSave = this.refreshDisplayedEvent(false, false, this.scopeCarried);
          this.scopeQuestionEnded();
          return this.drawerSave;
        })
        .catch(error => {
          this.scopeWriting = false;
          // Closing puts the previous participant list back, through
          // scopeDialogClosed: the whole visit, not only the changes that
          // gave a seat, since what the server refused may be any of it
          this.scopeSeatChanges = [];
          this.$refs.scopeDialog.close();
          this.changeFailed(error);
        })
        .finally(() => {
          this.drawerSave = null;
          this.settleQueuedSave();
          this.settleLiveRead();
        });
    },
    /**
     * Reads back what the server stored, whichever scope was chosen: the date
     * displayed may have gained a row of its own, and its padlock and its
     * participants may now come from itself rather than from the series — the
     * page has no way of deducing which. Best effort: the change is already
     * saved, so a failed read is not a failed save and says nothing.
     *
     * @param {Boolean} live whether the read follows a seat given elsewhere
     *          rather than a write of this page (see readSeatGivenElsewhere)
     * @param {Boolean} keepsDrawerList whether the people the drawer, still
     *          open, added or removed, not saved yet, stay on the list read:
     *          the read a closing makes from the padlock. A drawer left before
     *          it lands saves them once it did (see drawerClosed)
     * @param {Object} carried what the drawer held when the write this read
     *          follows took it (see drawerContent): what it changed since,
     *          open while that write was on its way, goes back on what is read
     * @param {Object} changes those changes, when the page replaced the
     *          event they were made on (see drawerChangesSince)
     * @returns {Promise} resolved once the read has landed, has been dropped
     *          for a later one, or has failed
     */
    refreshDisplayedEvent(live, keepsDrawerList, carried, changes) {
      if (!this.event) {
        return Promise.resolve();
      }
      // Only the latest read this section issued lands: a save's and a
      // closing's may overlap, each issued once its write was stored
      const read = ++this.readCount;
      const displayedEvent = this.event;
      // It covers every seat given before it is issued
      this.liveReadPending = false;
      this.readsInFlight++;
      const reloadPromise = this.event.occurrence && this.event.occurrence.id
        ? this.$eventService.getEventOccurrence(this.event.parent.id, this.event.occurrence.id, 'all,parentAll')
        : this.$eventService.getEventById(this.event.id, 'all,parentAll');
      return reloadPromise
        .then(storedEvent => {
          if (!storedEvent || read !== this.readCount) {
            return;
          }
          // What the organiser changed in the drawer, left open on a save
          // that gives a seat (EXO-91879), while that save was on its way
          let changedSince = carried ? this.drawerChangesSince(carried) : changes || null;
          const editedEvent = carried ? carried.event : displayedEvent;
          if (changedSince && this.eventKey(this.event) !== this.eventKey(editedEvent)) {
            // The page shows another event by now: nothing of those changes
            // goes on it
            this.saveChangesLeft(editedEvent, changedSince);
            changedSince = null;
          }
          if (displayedEvent !== this.event) {
            // The page replaced the event it shows, with a read of its own
            // made once the viewer answered: this one, issued before, would
            // put the previous answer back, and that read may predate what
            // this one was made for. It is made again on the event shown,
            // which the drawer's changes go on as well
            return this.refreshDisplayedEvent(live, keepsDrawerList, null, changedSince);
          }
          if (live && this.pageChanging()) {
            // The organiser changed the drawer, or started saving it, while
            // the read was on its way: it would replace those changes, so it
            // waits for them
            this.liveReadPending = true;
            return;
          }
          // The people the organiser added or removed in the drawer, still
          // open while the padlock closed the event, are not saved yet: they
          // go back on the list read (EXO-90310)
          const unsavedList = keepsDrawerList && this.drawerHoldsChanges() && this.attendeesChanged()
            ? this.attendeesDelta() : null;
          this.$set(this.event, 'open', storedEvent.open);
          this.$set(this.event, 'capacity', storedEvent.capacity);
          this.$set(this.event, 'acceptedCount', storedEvent.acceptedCount);
          // A change of the limit or of the participants moves the line
          this.$set(this.event, 'waitingListPosition', storedEvent.waitingListPosition);
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
          if (unsavedList) {
            // After the reference point, so that the drawer's close still
            // saves them
            this.$set(this.event, 'attendees', this.$agendaUtils.withAttendeesDelta(this.event.attendees, unsavedList));
          }
          if (changedSince && changedSince.attendees) {
            this.$set(this.event, 'attendees', this.$agendaUtils.withAttendeesDelta(this.event.attendees, changedSince.attendees));
          }
          if (changedSince && changedSince.capacity !== null) {
            // Never below the people who said Yes, as the stepper keeps it:
            // a step taken while the save was on its way did not count the
            // seats that save gave
            this.$set(this.event, 'capacity', changedSince.capacity && Math.max(changedSince.capacity, storedEvent.acceptedCount || 0));
          }
        })
        .catch(() => this.$root.$emit('agenda-refresh'))
        .finally(() => {
          // A seat given elsewhere while it was on its way may be missing
          // from what it read
          this.readsInFlight--;
          this.settleLiveRead();
        });
    },
    /**
     * The popup closed without the organiser choosing a scope: whatever the
     * drawer changed in place goes back to what it was. A question asked
     * while the drawer stays open, by changes that gave a seat (see
     * saveDrawerNow), undoes those changes alone, as long as the drawer is
     * still open on the date they were made on: the rest of the visit waits
     * for the close, as before they were made.
     *
     * @returns {void}
     */
    scopeDialogClosed() {
      if (!this.scopedChangePending) {
        return;
      }
      this.scopedChangePending = false;
      const seatChanges = this.scopeSeatChanges;
      this.scopeSeatChanges = [];
      if (seatChanges.length && this.drawerVisit && this.eventKey(this.event) === this.eventKey(this.scopeCarried.event)) {
        seatChanges.reverse().forEach(undo => undo());
      } else {
        this.restoreDrawerState();
      }
      this.scopeQuestionEnded();
      this.settleQueuedSave();
    },
    restoreDrawerState() {
      this.seatChanges = [];
      if (this.event && this.attendeesBackup) {
        this.$set(this.event, 'attendees', JSON.parse(JSON.stringify(this.attendeesBackup)));
        this.$set(this.event, 'open', this.openBackup);
        this.$set(this.event, 'capacity', this.capacityBackup);
        this.closeConfirmed = false;
      }
      // The changes a seat given elsewhere waited for are given up
      this.settleLiveRead();
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