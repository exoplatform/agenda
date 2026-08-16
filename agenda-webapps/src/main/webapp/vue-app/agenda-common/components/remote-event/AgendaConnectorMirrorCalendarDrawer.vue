<template>
  <exo-drawer
    ref="mirrorCalendarDrawer"
    class="agendaMirrorCalendarDrawer"
    right>
    <template slot="title">
      {{ $t('agenda.mirrorCalendar.title') }}
    </template>
    <template slot="content">
      <div class="d-flex flex-column mx-4 mt-4">
        <div class="text-subtitle">
          {{ $t('agenda.mirrorCalendar.introduction', {0: companyName}) }}
        </div>
        <v-alert
          v-if="creationRefused"
          type="info"
          class="mt-4 my-auto"
          dense
          text>
          {{ $t('agenda.mirrorCalendar.creationRefused') }}
        </v-alert>
        <div class="radio-group-container mt-2 ms-n1">
          <v-radio-group v-model="mode">
            <v-radio
              :disabled="creationRefused"
              value="create">
              <template #label>
                <span class="d-flex align-center">
                  <v-icon
                    :color="calendarColor"
                    size="16"
                    class="me-2">
                    fa-circle
                  </v-icon>
                  {{ $t('agenda.mirrorCalendar.createChoice', {0: calendarName}) }}
                </span>
              </template>
            </v-radio>
            <v-radio
              :disabled="!canPickExisting"
              :label="$t('agenda.mirrorCalendar.pickChoice')"
              value="existing" />
          </v-radio-group>
        </div>
        <div v-if="mode === 'existing'" class="agenda-mirror-calendar-choices ms-8">
          <v-progress-circular
            v-if="loadingCalendars"
            color="primary"
            size="20"
            width="2"
            indeterminate />
          <v-radio-group v-else v-model="selectedCalendarId">
            <v-radio
              v-for="calendar in calendars"
              :key="calendar.id"
              :value="calendar.id">
              <template #label>
                <span class="d-flex align-center text-truncate">
                  <v-icon
                    :color="calendar.color"
                    size="16"
                    class="me-2">
                    fa-circle
                  </v-icon>
                  {{ calendar.name }}
                </span>
              </template>
            </v-radio>
          </v-radio-group>
        </div>
      </div>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn me-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          :disabled="!canApply"
          :loading="saving"
          class="btn btn-primary"
          @click="apply">
          {{ $t('agenda.button.apply') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data: () => ({
    connector: null,
    mode: 'create',
    creationRefused: false,
    calendars: [],
    selectedCalendarId: null,
    loadingCalendars: false,
    saving: false,
    companyName: eXo.env.portal.companyName || '',
    calendarColor: '',
  }),
  computed: {
    /**
     * The name the mirror calendar will be created with: the platform
     * branding name plus "Meetings", composed through an i18n pattern so the
     * word order follows the language of the user at that moment. The name is
     * written once at creation and never renamed afterwards — the href is the
     * identity of the collection.
     *
     * @returns {String} the localised branded calendar name
     */
    calendarName() {
      return this.$t('agenda.mirrorCalendar.name', {0: this.companyName});
    },
    /**
     * Whether the existing-calendar choice can be offered: only when the
     * connector can enumerate calendars and the account holds at least one.
     *
     * @returns {Boolean} true when picking an existing calendar is possible
     */
    canPickExisting() {
      return this.calendars.length > 0;
    },
    /**
     * Whether the apply button should be active: always for a creation, only
     * once a calendar is chosen for the existing-calendar path.
     *
     * @returns {Boolean} true when the current choice can be applied
     */
    canApply() {
      return this.mode === 'create' && !this.creationRefused || !!this.selectedCalendarId;
    },
  },
  created() {
    this.$root.$on('agenda-connector-mirror-calendar-open', this.open);
  },
  beforeDestroy() {
    this.$root.$off('agenda-connector-mirror-calendar-open', this.open);
  },
  methods: {
    /**
     * Opens the drawer for a freshly connected connector, preselected on
     * creating the branded calendar — offered, not imposed: the user may
     * point the mirror at an existing calendar instead, or close the drawer.
     *
     * @param {Object} connector the connector that just connected
     * @returns {void}
     */
    open(connector) {
      if (!connector || !connector.canCreateCalendar) {
        return;
      }
      this.connector = connector;
      this.mode = 'create';
      this.creationRefused = false;
      this.selectedCalendarId = null;
      this.calendars = [];
      this.retrieveBranding();
      this.retrieveCalendars();
      if (this.$refs.mirrorCalendarDrawer) {
        this.$refs.mirrorCalendarDrawer.open();
      }
    },
    /**
     * Closes the drawer without touching the connector settings.
     *
     * @returns {void}
     */
    close() {
      if (this.$refs.mirrorCalendarDrawer) {
        this.$refs.mirrorCalendarDrawer.close();
      }
    },
    /**
     * Reads the platform branding, for the name and the colour the created
     * calendar carries: a white-labelled deployment must produce
     * "Acme Meetings" in the brand colour, never a hardcoded product name.
     *
     * @returns {Promise} resolves once the branding is read
     */
    retrieveBranding() {
      return this.$brandingService.getBrandingInformation()
        .then(branding => {
          this.companyName = branding && branding.companyName || this.companyName;
          this.calendarColor = branding && branding.themeStyle && branding.themeStyle.primaryColor || '';
        })
        .catch(() => {
          // the synchronous eXo.env.portal.companyName default remains
        });
    },
    /**
     * Loads the calendars of the connected account for the existing-calendar
     * choice, through the connector when it can enumerate them. The stored
     * mirror, when one exists, is kept out of the list — it only holds copies
     * of events eXo already displays.
     *
     * @returns {Promise} resolves once the list is loaded
     */
    retrieveCalendars() {
      if (typeof this.connector.listCalendars !== 'function') {
        return Promise.resolve();
      }
      this.loadingCalendars = true;
      return this.connector.listCalendars()
        .then(calendars => this.$remoteEventConnector.excludeMirrorCalendar(this.connector, calendars || []))
        .then(calendars => this.calendars = calendars.filter(calendar => !calendar.readOnly))
        .catch(error => console.error('cannot list the calendars of the connected account', error))
        .finally(() => this.loadingCalendars = false);
    },
    /**
     * Applies the choice of the user: creates the branded mirror calendar, or
     * stores the href of the calendar the user designated.
     *
     * @returns {Promise} resolves once the mirror destination is stored
     */
    apply() {
      this.saving = true;
      const outcome = this.mode === 'create'
        ? this.connector.createCalendar({
          name: this.calendarName,
          color: this.calendarColor,
          description: this.$t('agenda.mirrorCalendar.description', {0: this.companyName}),
        })
        : this.connector.setMirrorCalendar(this.selectedCalendarId);
      return outcome
        .then(() => {
          this.close();
          this.$root.$emit('alert-message', this.$t('agenda.mirrorCalendar.saved'), 'success');
        })
        .catch(error => this.handleFailure(error))
        .finally(() => this.saving = false);
    },
    /**
     * Handles a failed apply. A server refusing MKCALENDAR is not an eXo
     * error: it is explained here, at connect time, and the flow falls back
     * to choosing an existing calendar instead of failing at first push. Any
     * other failure is reported as an error.
     *
     * @param {Object} error the failure raised by the connector
     * @returns {void}
     */
    handleFailure(error) {
      if (error && error.calendarCreationRefused) {
        this.creationRefused = true;
        this.mode = this.canPickExisting && 'existing' || 'create';
        if (!this.canPickExisting) {
          this.$root.$emit('alert-message', this.$t('agenda.mirrorCalendar.noUsableCalendar'), 'error');
        }
      } else {
        console.error('cannot configure the mirror calendar', error);
        this.$root.$emit('alert-message', this.$t('agenda.mirrorCalendar.saveError'), 'error');
      }
    },
  },
};
</script>
