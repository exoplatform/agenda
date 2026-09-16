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
  <div>
    <div v-if="loading && !calendars.length" class="d-flex justify-center py-2">
      <v-progress-circular
        color="primary"
        size="20"
        width="2"
        indeterminate />
    </div>
    <v-list
      v-else
      class="pa-0"
      dense>
      <v-list-item
        v-for="calendar in calendars"
        :key="calendar.id"
        class="agenda-calendar-settings px-0">
        <!--
          The row's visible text is the name truncated to the width of
          whichever list is mounted, so the hover is where a name cut mid-way
          is recovered — and the name is what the cut takes: a connected
          account publishes its address inside the name, and two accounts give
          two calendars that diverge only there. A hover showing the
          description instead answered a question nobody asked and hid the one
          thing telling them apart.
        -->
        <v-list-item-content
          :title="calendarTooltip(calendar)"
          class="flex-grow-1 pa-0">
          <v-checkbox
            :input-value="isDisplayed(calendar)"
            :color="calendar.color"
            :label="calendarLabel(calendar)"
            class="agenda-calendar-settings-color ms-4"
            dense
            hide-details
            @change="toggle(calendar)" />
        </v-list-item-content>
        <!--
          A calendar that stopped synchronising sits in this list looking
          exactly like the ones that did not, which is why the notice cannot
          live only in the settings: nobody in that situation thinks to open
          them. The sentence is the connector's — only it knows what went
          wrong on its side.

          It belongs in the row's action area, at the end of the line: inside
          the content column it was laid out beneath the calendar's name,
          wrapping onto its own row and reading as though it belonged to the
          calendar below.
        -->
        <v-list-item-action
          v-if="problemOf(calendar)"
          class="my-0 ms-2">
          <v-tooltip bottom>
            <template #activator="{on, attrs}">
              <v-icon
                v-bind="attrs"
                size="14"
                color="warning"
                v-on="on">
                fa-exclamation-triangle
              </v-icon>
            </template>
            <span>{{ problemMessage(calendar) }}</span>
          </v-tooltip>
        </v-list-item-action>
        <!-- No action on a not-yet-persisted default calendar (id 0): it can
             only be edited once it exists, i.e. after the first event -->
        <!--
          Revealed on hover on a pointer device, always there on touch: three
          dots on every row turned a list of calendars into a column of
          controls. The class does the work in the panel's stylesheet, and
          focus-within keeps it reachable from the keyboard.
        -->
        <v-list-item-action
          v-if="calendar.id"
          class="my-0 ms-2 agenda-calendar-actions">
          <!--
            Asked again whenever the menu opens: a calendar just created is
            given its remote counterpart by an asynchronous listener after
            agenda has answered, so the refresh its creation emits can come too
            early for a connector to offer anything on it (EXO-90253).
          -->
          <v-menu
            :value="isRowMenuOpen(calendar.id)"
            content-class="agendaCalendarRowMenu"
            offset-y
            left
            @input="toggleRowMenu(calendar.id, $event)">
            <template #activator="{ on, attrs }">
              <v-btn
                v-bind="attrs"
                v-on="on"
                :title="$t('agenda.calendar.actions')"
                icon
                x-small
                @click="refreshCalendarMenu()">
                <v-icon size="14">fa-ellipsis-v</v-icon>
              </v-btn>
            </template>
            <v-list dense class="pa-0">
              <v-list-item @click="editCalendar(calendar)">
                <v-list-item-title>{{ $t('agenda.calendar.edit') }}</v-list-item-title>
              </v-list-item>
              <!--
                Whatever a connector adds for this calendar — the CalDAV
                add-on's "Share" (EXO-90253). Agenda names none of them: the
                connector answers its label already translated, and runs its
                own action. A connector declaring nothing adds no row.
              -->
              <v-list-item
                v-for="action in actionsOf(calendar)"
                :key="action.id"
                @click="runConnectorAction(action, calendar)">
                <v-list-item-title>{{ action.label }}</v-list-item-title>
              </v-list-item>
              <!--
                Publishing (EXO-90252): one entry naming the calendar's state and opening
                the drawer, which is where a calendar is unpublished.
                Right before Delete: Edit, then what connectors add (Share…,
                EXO-90253), then Publish — BlueMind's own order.
              -->
              <v-list-item
                :class="`agenda-calendar-link-action agenda-calendar-link-state-${linkStateOf(calendar)}`"
                @click="openCalendarLink(calendar)">
                <v-list-item-title class="d-flex align-center">
                  <v-icon
                    v-if="linkStateOf(calendar) === 'published'"
                    size="14"
                    class="me-2 success--text">
                    fas fa-check
                  </v-icon>
                  <v-icon
                    v-else-if="linkStateOf(calendar) === 'stopped'"
                    size="14"
                    class="me-2 warning--text">
                    fas fa-exclamation-triangle
                  </v-icon>
                  {{ publishMenuLabel(calendar) }}
                </v-list-item-title>
              </v-list-item>
              <v-list-item
                v-if="!calendar.system"
                @click="confirmDelete(calendar)">
                <v-list-item-title class="error--text">{{ $t('agenda.calendar.delete') }}</v-list-item-title>
              </v-list-item>
            </v-list>
          </v-menu>
        </v-list-item-action>
        <!--
          A published calendar (EXO-90252) carries a small sign, so a calendar anyone
          with its link can read does not look like the others; the colour and the
          glyph tell a working link from one that stopped. At the end of the row,
          to the right of the menu, which keeps its place while hidden. Same alignment
          as the read-only lock of the Remote rows (EXO-90236), and the same
          wrapper carrying the role and the label, since Vuetify hides a v-icon with
          no click listener from assistive technology.
        -->
        <v-list-item-action
          v-if="linkStateOf(calendar) !== 'none'"
          class="my-0 ms-2 flex-grow-0 justify-center agenda-calendar-published-icon">
          <span
            :title="publishedTooltip(calendar)"
            :aria-label="publishedTooltip(calendar)"
            :class="`d-flex agenda-calendar-published-${linkStateOf(calendar)}`"
            role="img">
            <v-icon
              :class="linkStateOf(calendar) === 'published' ? 'text-light-color' : 'warning--text'"
              size="14">
              {{ linkStateOf(calendar) === 'published' ? 'fas fa-link' : 'fas fa-unlink' }}
            </v-icon>
          </span>
        </v-list-item-action>
        <!--
          A calendar the user has shared with colleagues (EXO-90331). The inbound
          direction was already answered — a calendar shared WITH me carries its
          owner's avatar — while the outbound one showed nothing at all, so the
          only way to know which of one's own calendars were exposed was to open
          the Share drawer on each in turn. Same wrapper, same size and same
          alignment as the published sign above, since both are row states and a
          row may well carry the two at once.

          The click is a shortcut to the drawer that manages the share, taken
          from the very menu entry the connector already offers, so nothing here
          knows what a CalDAV share is. It is a shortcut and never the only path:
          the same drawer is one keyboard-reachable menu away on the same row,
          which is why the wrapper stays the published sign's plain labelled span
          rather than becoming a second button on every row.
        -->
        <v-list-item-action
          v-if="shareesOf(calendar)"
          class="my-0 ms-2 flex-grow-0 justify-center agenda-calendar-shared-icon"
          @click="openShare(calendar)">
          <span
            :title="sharedTooltip(calendar)"
            :aria-label="sharedTooltip(calendar)"
            class="d-flex"
            role="img">
            <v-icon class="text-light-color" size="14">fas fa-share-alt</v-icon>
          </span>
        </v-list-item-action>
      </v-list-item>
    </v-list>
    <exo-confirm-dialog
      ref="deleteConfirmDialog"
      :title="$t('agenda.calendarDelete.confirmTitle')"
      :message="deleteConfirmMessage"
      :ok-label="$t('agenda.calendar.delete')"
      :cancel-label="$t('agenda.button.cancel')"
      @ok="deleteCalendar" />
  </div>
</template>

<script>
import calendarLinkMenuMixin from '../../js/CalendarLinkMenuMixin.js';
import calendarRowMenuMixin from '../../js/CalendarRowMenuMixin.js';

export default {
  mixins: [calendarLinkMenuMixin, calendarRowMenuMixin],
  data: () => ({
    calendars: [],
    hiddenCalendarIds: [],
    problems: {},
    loading: false,
    calendarToDelete: null,
    connectorWarning: '',
    connectorActions: {},
    connectorActionsAsked: 0,
    problemsAsked: 0,
    shares: {},
    sharesAsked: 0,
  }),
  computed: {
    /**
     * The identity id of the current user, owner of every calendar listed
     * here.
     *
     * @returns {Number} current user identity id
     */
    userIdentityId() {
      return Number(eXo.env.portal.userIdentityId);
    },
    /**
     * The browser storage key holding the hidden calendar ids, scoped per
     * user so shared browsers don't leak one user's selection to another.
     *
     * @returns {String} local storage key
     */
    storageKey() {
      return `agenda.hiddenPersonalCalendars.${this.userIdentityId}`;
    },
    /**
     * The message of the deletion confirmation dialog, stating explicitly
     * that the events will be moved to the default calendar, never deleted.
     *
     * @returns {String} confirmation message for the calendar being deleted
     */
    deleteConfirmMessage() {
      if (!this.calendarToDelete) {
        return '';
      }
      const message = this.$t('agenda.calendarDelete.confirmMessage', {
        0: this.calendarLabel(this.calendarToDelete),
      });
      // A connector mirroring this calendar elsewhere knows something agenda
      // does not: that confirming also destroys a copy on a remote server,
      // events other devices added included. Agenda cannot phrase that — it
      // does not know which server, or whether the copy is eXo's to delete —
      // so the connector supplies the sentence and agenda shows it. It is
      // fetched before the dialog opens, never while this is computed: the
      // answer lives on a server, and a warning that arrives after the user
      // has confirmed is no warning at all.
      return this.connectorWarning && `${message}\n\n${this.connectorWarning}` || message;
    },
  },
  created() {
    this.hiddenCalendarIds = this.readHiddenCalendarIds();
    this.$root.$on('agenda-refresh-personal-calendars', this.retrieveProblems);
    // And on the agenda's general refresh, which is the one that follows a
    // synchronisation. Disconnecting deliberately pauses the bindings of the
    // calendars eXo created — so reconnecting finds the same collections
    // instead of making new ones — and reconnecting clears them a second
    // later. Refreshing only around the account changing caught that second
    // and then kept it: a warning on every one of the user's own calendars,
    // for a state that had already passed, until the page was reloaded.
    this.$root.$on('agenda-refresh', this.retrieveProblems);
    document.addEventListener('agenda-refresh-personal-calendars', this.retrieveProblems);
    // And once the connectors exist. This asks which calendars are failing,
    // and only a connector can answer — but a connector registers after its
    // own REST has answered, which is later than this component is created. So
    // the first ask finds nobody to ask, records that nothing is wrong, and
    // never revisits it: a calendar that had stopped synchronising was drawn
    // looking perfectly normal until something unrelated refreshed the panel.
    //
    // The caldav webapp already announces this — it dispatches
    // agenda-connectors-refresh once registration is done, and the admin
    // settings already listen to it. Listening to the same signal rather than
    // inventing another one, or worse, retrying on a timer.
    document.addEventListener('agenda-connectors-refresh', this.retrieveProblems);
    this.retrieveProblems();
    // What connectors add to a calendar's menu, asked at exactly the moments
    // its problems are, and for the same reasons: a connector registers after
    // this component is created; a drawer in another Vue app can only signal
    // on the document; and the general refresh follows every synchronisation,
    // which is what binds a new calendar to the collection it can be shared
    // through.
    this.$root.$on('agenda-refresh-personal-calendars', this.retrieveConnectorActions);
    this.$root.$on('agenda-refresh', this.retrieveConnectorActions);
    document.addEventListener('agenda-refresh-personal-calendars', this.retrieveConnectorActions);
    document.addEventListener('agenda-connectors-refresh', this.retrieveConnectorActions);
    this.retrieveConnectorActions();
    // Which of these calendars colleagues can see (EXO-90331), asked at exactly
    // the same four moments and for exactly the same reasons: a connector
    // registers after this component is created, a drawer in another Vue app
    // can only signal on the document, and the general refresh is what follows
    // a synchronisation — which is the pass that observes a share appearing or
    // going away in the first place.
    this.$root.$on('agenda-refresh-personal-calendars', this.retrieveShares);
    this.$root.$on('agenda-refresh', this.retrieveShares);
    document.addEventListener('agenda-refresh-personal-calendars', this.retrieveShares);
    document.addEventListener('agenda-connectors-refresh', this.retrieveShares);
    this.retrieveShares();
    this.$root.$on('agenda-refresh-personal-calendars', this.retrieveCalendars);
    // Also on the document, so an add-on's drawer living in another Vue app —
    // the settings page has its own — can say that the set of personal
    // calendars just changed. A $root event never crosses that boundary.
    document.addEventListener('agenda-refresh-personal-calendars', this.retrieveCalendars);
    this.retrieveCalendars();
  },
  beforeDestroy() {
    this.$root.$off('agenda-refresh-personal-calendars', this.retrieveCalendars);
    document.removeEventListener('agenda-refresh-personal-calendars', this.retrieveCalendars);
    this.$root.$off('agenda-refresh-personal-calendars', this.retrieveProblems);
    this.$root.$off('agenda-refresh', this.retrieveProblems);
    document.removeEventListener('agenda-refresh-personal-calendars', this.retrieveProblems);
    document.removeEventListener('agenda-connectors-refresh', this.retrieveProblems);
    this.$root.$off('agenda-refresh-personal-calendars', this.retrieveConnectorActions);
    this.$root.$off('agenda-refresh', this.retrieveConnectorActions);
    document.removeEventListener('agenda-refresh-personal-calendars', this.retrieveConnectorActions);
    document.removeEventListener('agenda-connectors-refresh', this.retrieveConnectorActions);
    this.$root.$off('agenda-refresh-personal-calendars', this.retrieveShares);
    this.$root.$off('agenda-refresh', this.retrieveShares);
    document.removeEventListener('agenda-refresh-personal-calendars', this.retrieveShares);
    document.removeEventListener('agenda-connectors-refresh', this.retrieveShares);
  },
  methods: {
    /**
     * What each connector says is wrong with one of this user's calendars.
     *
     * Asked of the connectors rather than worked out here: which calendar is
     * failing, and why, is the connector's own business — agenda only knows
     * that a row should carry a warning and what sentence to show on it.
     *
     * Only the latest question's answer is kept: the list asks again on every
     * signal and whenever a calendar's menu opens, and an older, slower answer
     * must not replace a newer one.
     *
     * @returns {Promise} resolves once every connector has answered
     */
    retrieveProblems() {
      const asked = ++this.problemsAsked;
      const connectors = (extensionRegistry.loadExtensions('agenda', 'connectors') || [])
        .filter(connector => connector && connector.connected && typeof connector.calendarProblems === 'function');
      if (!connectors.length) {
        this.problems = {};
        return Promise.resolve();
      }
      return Promise.all(connectors.map(connector => Promise.resolve(connector.calendarProblems())
        .catch(() => ({}))))
        .then(answers => {
          if (asked === this.problemsAsked) {
            this.problems = Object.assign({}, ...answers);
          }
        });
    },
    /**
     * What each connector adds to the menu of one of this user's calendars.
     *
     * Asked of the connectors, like their problems: which calendar a connector
     * can act on, and what the action is called, are its own business. Agenda
     * keeps only an id, the label as given, and which connector offered it, so
     * a click goes back to that connector. Two connectors offering the same
     * action id on one calendar — the same add-on registered once per server —
     * give one row.
     *
     * Not gated on the connector being marked connected. The connector
     * component sets that flag once the user's settings have loaded, and
     * nothing tells this list when that happens, so a connector asked too early
     * would not be asked again. Which calendars an action applies to is the
     * connector's to answer, and one with no account answers none.
     *
     * @returns {Promise} resolves once every connector has answered
     */
    retrieveConnectorActions() {
      // Only the latest question's answer is kept: an older, slower answer
      // arriving after a newer one would bring back a menu from before.
      const asked = ++this.connectorActionsAsked;
      const connectors = this.connectors()
        .filter(connector => connector
          && typeof connector.calendarActions === 'function'
          && typeof connector.runCalendarAction === 'function');
      if (!connectors.length) {
        this.connectorActions = {};
        return Promise.resolve();
      }
      return Promise.all(connectors.map(connector => Promise.resolve(connector.calendarActions())
        .then(answer => ({connector: connector.name, answer: answer || {}}))
        .catch(() => ({connector: connector.name, answer: {}}))))
        .then(answers => {
          const actions = {};
          answers.forEach(({connector, answer}) => Object.keys(answer).forEach(calendarId => {
            (answer[calendarId] || []).filter(action => action && action.id && action.label).forEach(action => {
              const row = actions[calendarId] || (actions[calendarId] = []);
              if (!row.some(known => known.id === action.id)) {
                row.push({id: action.id, label: action.label, connector});
              }
            });
          }));
          if (asked === this.connectorActionsAsked) {
            this.connectorActions = actions;
          }
        });
    },
    /**
     * Asks the connectors again about actions and problems whenever the
     * button of a calendar's menu is clicked: a calendar created during the
     * session is bound to its CalDAV collection asynchronously, after agenda's
     * own refresh signal, so its "Share" action may only exist by the time its
     * menu is opened. Bound to the activator button's click rather than the
     * menu's input so the menu's open state stays wholly the list's own; a
     * click that closes the menu asks once more, which costs one request.
     *
     * @returns {Promise} resolves once both questions are answered
     */
    refreshCalendarMenu() {
      return Promise.all([this.retrieveConnectorActions(), this.retrieveProblems(), this.retrieveShares()]);
    },
    /**
     * Which of this user's calendars each connector says colleagues can see,
     * and how many of them.
     *
     * Asked of the connectors, like their problems and their menu entries:
     * whether a calendar is exposed anywhere outside eXo, and to how many
     * people, is knowledge only the connector holds. Agenda keeps the number,
     * the id of the action that manages the share, and which connector
     * answered, so a click on the mark goes back to that connector without
     * agenda knowing any of its action names.
     *
     * Not gated on the connector being marked connected, for the reason
     * retrieveConnectorActions is not: that flag is set once the user's
     * settings have loaded and nothing tells this list when that happens, so a
     * connector asked too early would never be asked again. A connector with no
     * account answers nothing, which is the right answer.
     *
     * Only the latest question's answer is kept: an older, slower answer
     * arriving after a newer one would bring back a mark the user has just
     * removed.
     *
     * @returns {Promise} resolves once every connector has answered
     */
    retrieveShares() {
      const asked = ++this.sharesAsked;
      const connectors = this.connectors()
        .filter(connector => connector && typeof connector.calendarShares === 'function');
      if (!connectors.length) {
        this.shares = {};
        return Promise.resolve();
      }
      return Promise.all(connectors.map(connector => Promise.resolve(connector.calendarShares())
        .then(answer => ({connector: connector.name, answer: answer || {}}))
        .catch(() => ({connector: connector.name, answer: {}}))))
        .then(answers => {
          const shares = {};
          answers.forEach(({connector, answer}) => Object.keys(answer).forEach(calendarId => {
            const share = answer[calendarId];
            const sharees = share && Number(share.sharees) || 0;
            if (sharees > 0) {
              // Two connectors answering for one calendar is the same add-on
              // registered once per declared server; the calendar lives on one
              // of them, so the larger count is the one that saw it.
              const known = shares[calendarId];
              if (!known || known.sharees < sharees) {
                shares[calendarId] = {sharees, actionId: share.actionId, connector};
              }
            }
          }));
          if (asked === this.sharesAsked) {
            this.shares = shares;
          }
        });
    },
    /**
     * How many colleagues see one calendar, when any does.
     *
     * @param {Object} calendar the row being drawn
     * @returns {Object} the share as retrieveShares kept it, or null when the
     *          calendar is not seen by anyone
     */
    shareesOf(calendar) {
      return calendar && this.shares[calendar.id] || null;
    },
    /**
     * What the share mark says when the pointer rests on it.
     *
     * The number is a floor and the sentence is worded as one. A connector
     * counts a share this platform itself made as soon as it is made, but a
     * share made in the calendar account's own web client it can only observe,
     * which leaves out anyone who is not a user of this platform with a
     * connected account and lags such a share by a few minutes; and a calendar
     * this platform did not create on the account, only imported from it, can
     * be shared and is never counted. Naming the drawer is the rest of the
     * answer — only the drawer knows who, read from the server itself.
     *
     * @param {Object} calendar the calendar the mark sits on
     * @returns {String} the sentence to show, empty when there is no mark
     */
    sharedTooltip(calendar) {
      const share = this.shareesOf(calendar);
      return share ? this.$t('agenda.calendars.sharedTooltip', {0: share.sharees}) : '';
    },
    /**
     * Opens whatever manages the share of a calendar, from its mark.
     *
     * The very entry the connector already offers in the row's menu, found by
     * the id the connector named in its own answer and handed back through
     * runConnectorAction — so the drawer is opened by exactly one piece of code
     * whichever of the two ways the user reached it, and agenda hardcodes no
     * connector's action name.
     *
     * Does nothing when that entry is not offered right now: a calendar can be
     * seen by colleagues while the connector says it can no longer be shared
     * from eXo — an unreachable server, a calendar no longer owned on it — and
     * a mark that opens an empty drawer would be worse than one that only
     * informs.
     *
     * @param {Object} calendar the calendar the mark sits on
     * @returns {Promise} resolves once the connector has taken it
     */
    openShare(calendar) {
      const share = this.shareesOf(calendar);
      const offered = share && this.actionsOf(calendar).filter(one => one.id === share.actionId) || [];
      // The connector that reported the count first, and any connector
      // offering the same action second. The two are the same in every shape
      // seen so far, but they are kept in step by two different rules — the
      // mark keeps the largest count, the menu keeps the first offer — and a
      // mark that silently did nothing because those rules disagreed would be
      // indistinguishable from a broken drawer. The action id is the
      // connector's own, and an add-on registered once per server dispatches
      // the same drawer from either descriptor, so the fallback cannot open
      // somebody else's.
      const action = offered.find(one => one.connector === share.connector) || offered[0];
      return action ? this.runConnectorAction(action, calendar) : Promise.resolve();
    },
    /**
     * The connector actions offered on one calendar.
     *
     * @param {Object} calendar the row being drawn
     * @returns {Array} the actions, possibly empty
     */
    actionsOf(calendar) {
      return calendar && this.connectorActions[calendar.id] || [];
    },
    /**
     * Hands a menu action back to the connector that offered it, with the
     * calendar as this list names it — the unnamed default calendar is "My
     * calendar" here, not its owner's name.
     *
     * @param {Object} action the action as retrieveConnectorActions kept it
     * @param {Object} calendar the calendar the menu belongs to
     * @returns {Promise} resolves once the connector has taken it
     */
    runConnectorAction(action, calendar) {
      const connector = this.connectors().find(one => one && one.name === action.connector);
      if (!connector || typeof connector.runCalendarAction !== 'function') {
        return Promise.resolve();
      }
      return Promise.resolve(connector.runCalendarAction(action.id,
        Object.assign({}, calendar, {name: this.calendarLabel(calendar)})))
        .catch(error => console.error(`cannot run ${action.id} on calendar ${calendar.id}`, error));
    },
    /**
     * @param {Object} calendar the row being drawn
     * @returns {Object} what is wrong with it, or null when nothing is
     */
    /**
     * What the marker says when the pointer rests on it.
     *
     * The connector's own sentence when it has one — only it knows what went
     * wrong on its side — and agenda's plain one when it does not. A marker
     * that cannot say anything is worse than no marker: the user sees that
     * something is wrong and has no way to find out what, which is exactly
     * what an empty tooltip delivered when the connector's bundle was served
     * incomplete.
     *
     * @param {Object} calendar the calendar the marker sits on
     * @returns {String} the sentence to show
     */
    problemMessage(calendar) {
      const problem = this.problemOf(calendar);
      return problem && problem.message || this.$t('agenda.calendars.problemUnknown');
    },
    problemOf(calendar) {
      return calendar && this.problems[calendar.id] || null;
    },
    /**
     * Retrieves the personal calendars of the current user. The REST list
     * endpoint returns a not-yet-persisted default calendar (id 0) when the
     * user never used the agenda: it is displayed the same way, and becomes a
     * real row on first use.
     *
     * @returns {Promise} resolved when the list is refreshed
     */
    retrieveCalendars() {
      this.loading = true;
      return this.$calendarService.getCalendars(0, 100, false, [this.userIdentityId])
        .then(data => {
          this.calendars = data && data.calendars || [];
          // The undeletable default calendar first, then by name
          this.calendars.sort((calendar1, calendar2) => (calendar2.system - calendar1.system)
            || this.calendarLabel(calendar1).localeCompare(this.calendarLabel(calendar2)));
        })
        .finally(() => this.loading = false);
    },
    /**
     * The display label of a calendar: its user-defined name when it has one,
     * else the localized 'My calendar' for the unnamed default — the server
     * keeps returning the owner display name, which is not a useful label for
     * one's own calendar.
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
     * The hover text of a calendar row: the calendar's name, then its
     * description on a second line when it has one.
     *
     * <p>
     * The name comes first and is never dropped. The row shows it truncated
     * to the width of the list it is in, so the hover is the only place a
     * name cut mid-way can be read whole — and it is precisely the end of the
     * name that the cut takes, where a connected account writes its address.
     * Two accounts produce two calendars whose names differ only there, and a
     * hover carrying the description instead left them indistinguishable.
     *
     * @param {Object} calendar the calendar to describe
     * @returns {String} the hover text
     */
    calendarTooltip(calendar) {
      const label = this.calendarLabel(calendar);
      return calendar.description ? `${label}\n${calendar.description}` : label;
    },
    /**
     * Whether the events of a calendar are currently displayed in the agenda.
     *
     * @param {Object} calendar the calendar to check
     * @returns {Boolean} true when displayed
     */
    isDisplayed(calendar) {
      return this.hiddenCalendarIds.indexOf(Number(calendar.id)) < 0;
    },
    /**
     * Shows or hides the events of a calendar, persists the choice in the
     * browser storage and notifies the agenda so the grid filters
     * accordingly.
     *
     * @param {Object} calendar the calendar to toggle
     * @returns {void}
     */
    toggle(calendar) {
      const calendarId = Number(calendar.id);
      const index = this.hiddenCalendarIds.indexOf(calendarId);
      if (index < 0) {
        this.hiddenCalendarIds.push(calendarId);
      } else {
        this.hiddenCalendarIds.splice(index, 1);
      }
      localStorage.setItem(this.storageKey, JSON.stringify(this.hiddenCalendarIds));
      this.$root.$emit('agenda-personal-calendars-visibility-changed', this.hiddenCalendarIds.slice());
    },
    /**
     * Reads the hidden calendar ids persisted in the browser storage.
     *
     * @returns {Array} hidden calendar ids as numbers
     */
    readHiddenCalendarIds() {
      try {
        const storedValue = localStorage.getItem(this.storageKey);
        const hiddenIds = storedValue && JSON.parse(storedValue) || [];
        return Array.isArray(hiddenIds) ? hiddenIds.map(Number) : [];
      } catch (e) {
        return [];
      }
    },
    /**
     * Opens the calendar drawer pre-filled with the calendar to edit: name,
     * description and color are all edited there, in the same drawer that
     * creates calendars.
     *
     * @param {Object} calendar the calendar to edit
     * @returns {void}
     */
    editCalendar(calendar) {
      this.$root.$emit('agenda-personal-calendar-drawer-open', calendar);
    },
    /**
     * The connectors registered with agenda, in the shape they register
     * themselves.
     *
     * @returns {Array} the registered connectors, possibly empty
     */
    connectors() {
      return extensionRegistry.loadExtensions('agenda', 'connectors') || [];
    },

    /**
     * Asks every connector what deleting this calendar would also do, and
     * keeps the sentence the one that claims it wants shown.
     *
     * Asked once, before the dialog opens. A connector answering slowly delays
     * the dialog rather than letting it open without the warning — which is
     * the right trade: the whole point of the sentence is to be read before
     * the user confirms, not after.
     *
     * @param {Object} calendar the calendar about to be deleted
     * @returns {Promise} resolves once the warning is known
     */
    loadConnectorWarning(calendar) {
      this.connectorWarning = '';
      const connector = this.connectors().find(one => one && typeof one.describeCalendarDeletion === 'function');
      if (!connector) {
        return Promise.resolve();
      }
      return connector.describeCalendarDeletion(calendar)
        .then(description => {
          this.connectorWarning = description && description.claims && description.warning || '';
        })
        .catch(() => {
          // A connector that cannot answer must not stop the user deleting a
          // calendar. The dialog opens without its sentence, and the deletion
          // call itself still decides what happens remotely.
          this.connectorWarning = '';
        });
    },
    /**
     * Removes whatever a connector mirrors this calendar as, before agenda
     * removes the calendar itself.
     *
     * Resolves immediately when no connector claims it, which is every case
     * that existed before this hook.
     *
     * @param {Object} calendar the calendar being deleted
     * @returns {Promise} resolves once the remote side is gone, rejects to
     *          abort the whole deletion
     */
    deleteRemoteCounterpart(calendar) {
      const connector = this.connectors().find(one => one && typeof one.deleteCalendar === 'function');
      if (!connector) {
        return Promise.resolve();
      }
      return connector.deleteCalendar(calendar);
    },
    /**
     * Opens the deletion confirmation dialog for a calendar, stating that its
     * events will be moved to the default calendar.
     *
     * @param {Object} calendar the calendar to delete
     * @returns {void}
     */
    confirmDelete(calendar) {
      this.calendarToDelete = calendar;
      this.loadConnectorWarning(calendar).then(() => this.$refs.deleteConfirmDialog.open());
    },
    /**
     * Deletes the calendar confirmed by the user: server-side its events are
     * moved to the default calendar, so the agenda is refreshed afterwards to
     * show them under their new color.
     *
     * @returns {void}
     */
    deleteCalendar() {
      if (!this.calendarToDelete) {
        return;
      }
      const calendarId = this.calendarToDelete.id;
      const calendar = this.calendarToDelete;
      // The remote side first, and only then the local one. A connector that
      // fails here must leave BOTH sides untouched: deleting locally first can
      // strand a collection on a server after the record that knew about it is
      // gone, and nothing will ever find it again. So a rejection stops the
      // whole deletion rather than being reported after the fact.
      this.deleteRemoteCounterpart(calendar)
        .then(() => this.$calendarService.deleteCalendar(calendarId))
        .then(() => {
          this.calendarToDelete = null;
          return this.retrieveCalendars();
        })
        .then(() => this.$root.$emit('agenda-refresh'))
        // A connector that refused knows why, and agenda does not: which
        // server answered, and whether anything was deleted at all. It rejects
        // with a message already in the user's language, and that message is
        // shown rather than agenda's generic one — "nothing was deleted, in
        // eXo or on the server" is a very different thing to read than "the
        // calendar could not be deleted".
        .catch(error => this.$root.$emit('alert-message',
          error && error.message || this.$t('agenda.calendarDelete.error'),
          'error'));
    },
  },
};
</script>
