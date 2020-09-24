<template>
  <exo-drawer
    ref="UserSettingAgendaDrawer"
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
            <select v-model="settings.agendaDefaultView" class="width-auto my-auto pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
              <option value="day">{{ $t('agenda.label.viewDay') }}</option>
              <option value="week">{{ $t('agenda.label.viewWeek') }}</option>
              <option value="month">{{ $t('agenda.label.viewMonth') }}</option>
            </select>
          </div>
          <div class="d-flex flex-column mb-5">
            <label class="switch-label-text mt-1 text-subtitle-1">{{ $t('agenda.settings.drawer.label.WeekStartOn') }}:</label>
            <select v-model="settings.agendaWeekStartOn" class="width-auto my-auto pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
              <option
                v-for="day in DAYS_ABBREVIATIONS"
                :key="day"
                :value="day">
                {{ getDayFromAbbreviation(day) }}
              </option>
            </select>
          </div>

          <div class="d-flex flex-row mt-5">
            <label class="switch-label-text mt-1 text-subtitle-1">{{ $t('agenda.settings.drawer.label.showWorkingTime') }}:</label>
            <v-switch v-model="settings.showWorkingTime" class="mt-0 ml-4" />
          </div>
          <div class="d-flex flex-row align-baseline">
            <input
              id="workingTimeStart"
              v-model="settings.workingTimeStart"
              type="time"
              name="workingTimeStart"
              :disabled="!settings.showWorkingTime">

            <label class="switch-label-text mx-5 text-subtitle-1" :class="{'disabled': !settings.showWorkingTime}">{{ $t('agenda.label.to') }}</label>
            <input
              id="workingTimeEnd"
              v-model="settings.workingTimeEnd"
              type="time"
              name="workingTimeEnd"
              :min="settings.workingTimeStart"
              :disabled="!settings.showWorkingTime">
          </div>
        </v-layout>
      </v-form>
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
      DAYS_ABBREVIATIONS: ['SU', 'MO','TU','WE','TH','FR', 'SA']
    };
  },
  created() {
    this.$root.$on('user-settings-agenda-drawer-open', this.open);
  },
  methods: {
    open() {
      this.$refs.UserSettingAgendaDrawer.open();
    },
    close() {
      this.$refs.UserSettingAgendaDrawer.close();
    },
    save() {
      if(this.validateForm()) {
        this.$refs.UserSettingAgendaDrawer.startLoading();
        this.$calendarService.saveAgendaSettings(this.settings).then(() => {
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
    }
  },
};
</script>

