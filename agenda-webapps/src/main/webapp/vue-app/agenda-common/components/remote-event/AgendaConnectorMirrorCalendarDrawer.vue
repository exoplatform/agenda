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
        <!--
          Creating is the only offer. Pointing the mirror at a calendar the
          user already keeps would push eXo's copies into it and then read them
          back for display, showing every accepted meeting twice — once as the
          space event, once as its own copy. Excluding that calendar from the
          agenda instead would make a calendar they rely on quietly disappear.
          Neither is something to hand a user who is picking from a list, and
          most would reach for their work calendar without suspecting either.
        -->
        <div class="agenda-mirror-calendar-choice mt-4">
          <v-icon
            :color="calendarColor"
            size="16"
            class="me-2">
            fa-circle
          </v-icon>
          <span class="font-weight-bold">{{ $t('agenda.mirrorCalendar.createChoice', {0: calendarName}) }}</span>
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
    creationRefused: false,
    calendars: [],
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
     * Whether the apply button should be active. It is not once the server has
     * refused to create the calendar: there is nothing left to apply, and the
     * message explains where meetings will go instead.
     *
     * @returns {Boolean} true when the creation can still be applied
     */
    canApply() {
      return !this.creationRefused;
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
      this.creationRefused = false;
      this.retrieveBranding();
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
     * Creates the branded calendar that will receive the meetings eXo pushes.
     *
     * @returns {Promise} resolves once the mirror destination is stored
     */
    apply() {
      this.saving = true;
      return this.connector.createCalendar({
        name: this.calendarName,
        color: this.calendarColor,
        description: this.$t('agenda.mirrorCalendar.description', {0: this.companyName}),
      })
        .then(() => {
          this.close();
          this.$root.$emit('alert-message', this.$t('agenda.mirrorCalendar.saved'), 'success');
        })
        .catch(error => this.handleFailure(error))
        .finally(() => this.saving = false);
    },
    /**
     * Handles a failed apply. A server refusing to create a calendar is not
     * an eXo error: it is explained here, at connect time, rather than
     * failing silently at the first push. Meetings then go to the account's
     * first calendar, which is what the connector does without a stored
     * destination. Any other failure is reported as an error.
     *
     * @param {Object} error the failure raised by the connector
     * @returns {void}
     */
    handleFailure(error) {
      if (error && error.calendarCreationRefused) {
        this.creationRefused = true;
      } else {
        console.error('cannot configure the mirror calendar', error);
        this.$root.$emit('alert-message', this.$t('agenda.mirrorCalendar.saveError'), 'error');
      }
    },
  },
};
</script>
