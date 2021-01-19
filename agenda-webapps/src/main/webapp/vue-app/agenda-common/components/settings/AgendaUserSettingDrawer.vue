<template>
  <exo-drawer
    ref="UserSettingAgendaDrawer"
    :confirm-close="confirmClose"
    :confirm-close-labels="confirmCloseLabels"
    class="UserSettingAgendaDrawer"
    body-classes="hide-scroll decrease-z-index-more"
    right>
    <template slot="title">
      {{ $t('agenda.settings.drawer.title') }}
    </template>
    <template slot="content">
      <v-form
        ref="agendaSettingsForm"
        class="flex"
        flat>
        <v-layout class="ma-5 d-flex flex-column">
          <div class="d-flex flex-column mb-5">
            <label class="switch-label-text mt-1 text-subtitle-1">{{ $t('agenda.settings.drawer.label.DefaultView') }}:</label>
            <select v-model="userSettingsForm.agendaDefaultView" class="width-auto my-auto pr-2 ignore-vuetify-classes d-none d-sm-inline">
              <option value="day">{{ $t('agenda.label.viewDay') }}</option>
              <option value="week">{{ $t('agenda.label.viewWeek') }}</option>
              <option value="month">{{ $t('agenda.label.viewMonth') }}</option>
            </select>
          </div>
          <div class="d-flex flex-column mb-5">
            <label class="switch-label-text mt-1 text-subtitle-1">{{ $t('agenda.settings.drawer.label.WeekStartOn') }}:</label>
            <select v-model="userSettingsForm.agendaWeekStartOn" class="width-auto my-auto pr-2 ignore-vuetify-classes d-none d-sm-inline">
              <option
                v-for="day in DAYS_ABBREVIATIONS"
                :key="day"
                :value="day">
                {{ DAY_NAME_BY_ABBREVIATION[day] }}
              </option>
            </select>
          </div>

          <div class="d-flex flex-row">
            <label class="switch-label-text mt-1 text-subtitle-1">{{ $t('agenda.settings.drawer.label.showWorkingTime') }}:</label>
            <v-switch v-model="userSettingsForm.showWorkingTime" class="mt-0 ml-4" />
          </div>
          <div v-if="userSettingsForm.showWorkingTime" class="workingTime d-flex flex-row align-center">
            <time-picker
              v-model="userSettingsForm.workingTimeStart"
              interval-minutes="$agendaUtils.MINIMUM_TIME_INTERVAL" />
            <label class="switch-label-text mx-5 text-subtitle-1">{{ $t('agenda.label.to') }}</label>
            <time-picker
              v-model="userSettingsForm.workingTimeEnd"
              interval-minutes="$agendaUtils.MINIMUM_TIME_INTERVAL" />
          </div>
          <label class="subtitle-1 float-left mt-5 mr-4">
            {{ $t('agenda.label.defaultReminders') }}
          </label>
          <agenda-reminder-user-settings ref="reminders" :reminders="userSettingsForm.reminders" />
        </v-layout>
      </v-form>
      <exo-confirm-dialog
        ref="CancelSavingChangesDialog"
        :message="$t('agenda.settings.drawer.confirmCancelChanges')"
        :title="$t('agenda.settings.drawer.confirmCancelChanges.title')"
        :ok-label="$t('agenda.button.ok')"
        :cancel-label="$t('agenda.button.cancel')"
        @ok="confirmCancelSavingChanges()" />
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn mr-2"
          @click="close">
          <template>
            {{ $t('agenda.button.cancel') }}
          </template>
        </v-btn>
        <v-btn
          class="btn btn-primary"
          @click="save">
          {{ $t('agenda.button.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => ({}),
    },
  },
  data() {
    return {
      DAYS_ABBREVIATIONS: ['SU', 'MO','TU','WE','TH','FR', 'SA'],
      userSettingsForm: {},
      saved: false,
    };
  },
  computed: {
    DAY_NAME_BY_ABBREVIATION () {
      return {
        'MO': this.getDayFromAbbreviation('MO'),
        'TU': this.getDayFromAbbreviation('TU'),
        'WE': this.getDayFromAbbreviation('WE'),
        'TH': this.getDayFromAbbreviation('TH'),
        'FR': this.getDayFromAbbreviation('FR'),
        'SA': this.getDayFromAbbreviation('SA'),
        'SU': this.getDayFromAbbreviation('SU'),
      };
    },
    confirmCloseLabels() {
      return {
        title: this.$t('agenda.settings.drawer.confirmCancelChanges.title'),
        message: this.$t('agenda.settings.drawer.confirmCancelChanges'),
        ok: this.$t('agenda.button.yes'),
        cancel: this.$t('agenda.button.no'),
      };
    },
    confirmClose() {
      return !this.saved && !this.$agendaUtils.areSameObjects(this.userSettingsForm, this.settings);
    },
    showWorkingTime() {
      return this.userSettingsForm.showWorkingTime;
    },
  },
  watch: {
    showWorkingTime(newVal, oldVal) {
      if (newVal && !oldVal) {
        this.userSettingsForm.workingTimeStart = this.settings.workingTimeStart || '09:00';
        this.userSettingsForm.workingTimeEnd = this.settings.workingTimeEnd || '18:00';
      }
    },
  },
  created() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
    this.$root.$on('user-settings-agenda-drawer-open', this.open);
  },
  methods: {
    open() {
      this.userSettingsForm = this.settings && JSON.parse(JSON.stringify(this.settings)) || {};
      this.saved = false;
      this.$refs.UserSettingAgendaDrawer.open();
    },
    close() {
      this.$refs.UserSettingAgendaDrawer.close();
    },
    save() {
      if(this.validateForm()) {
        this.$refs.UserSettingAgendaDrawer.startLoading();
        this.$settingsService.saveUserSettings(this.userSettingsForm)
          .then(() => {
            this.saved = true;
            return this.$nextTick();
          })
          .then(() => {
            this.$root.$emit('agenda-settings-refresh', this.userSettingsForm);
            this.$refs.UserSettingAgendaDrawer.close();
          })
          .finally(() => {
            this.$refs.UserSettingAgendaDrawer.endLoading();
          });
      }
    },
    getDayFromAbbreviation(day) {
      return this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
    },
    validateForm() {
      if (!this.$refs.agendaSettingsForm.validate() // Vuetify rules
          || !this.$refs.agendaSettingsForm.$el.reportValidity()) { // Standard HTML rules
        return;
      }
      return true;
    },
    confirmCancelSavingChanges() {
      this.$refs.UserSettingAgendaDrawer.close();
    }
  },
};
</script>