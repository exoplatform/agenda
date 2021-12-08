<template>
  <v-list-item>
    <v-list-item-content>
      <v-list-item-title class="title text-color">
        {{ $t('agenda') }}
      </v-list-item-title>
      <v-list-item-subtitle class="text-sub-title">
        <v-list-item v-if="settings" dense>
          <v-list-item-content class="pa-0">
            <v-list-item-title class="text-wrap">
              <template>
                <v-chip
                  class="ma-2"
                  color="primary"
                  style="max-width:calc(100% - 40px)">
                  <span class="text-truncate">
                    <span class="text-capitalize">{{ agendaSelectedView }}</span>
                    <span class="ps-1">{{ $t('agenda.view') }} </span>
                  </span>
                </v-chip>
                <v-chip
                  class="ma-2"
                  color="primary"
                  style="max-width:calc(100% - 40px)"
                  >
                  <span class="text-truncate" >
                   {{ agendaWeekStartOnLabel }}
                  </span>
                </v-chip>
                <v-chip
                  v-if="agendaWorkingTime"
                  class="ma-2"
                  color="primary"
                  style="max-width:calc(100% - 40px)"
                  >
                  <span class="text-truncate">
                  {{ agendaWorkingTime }}
                  </span>
                
                </v-chip>
                <template v-if="settings.reminders">
                  <v-chip
                    v-for="(reminder, index) in settings.reminders"
                    :key="index"
                    class="ma-2"
                    color="primary"
                    style="max-width:calc(100% - 40px)">
                    <template v-if="reminder.before">
                      <span class="text-truncate">
                       {{ $t('agenda.label.notifyMeBefore', {0: reminder.before, 1: $t(`agenda.option.${reminder.beforePeriodType.toLowerCase()}s`).toLowerCase()}) }}
                      </span>
                      </template>
                    <template v-else>
                      <span class="text-truncate">
                      {{ $t('agenda.label.notifyMeWhenEventStarts') }}
                       </span>
                    </template>
                    
                  </v-chip>
                </template>
              </template>
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action>
      <v-btn
        small
        icon
        @click="openDrawer">
        <i class="uiIconEdit uiIconLightBlue pb-2"></i>
      </v-btn>
    </v-list-item-action>
    <agenda-user-setting-drawer ref="agendaDrawer" :settings="settings" />
  </v-list-item>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    id: `Settings${parseInt(Math.random() * 10000)
      .toString()
      .toString()}`,
  }),
  computed: {
    isMobile() {
      return this.$vuetify && this.$vuetify.breakpoint && this.$vuetify.breakpoint.name === 'xs';
    },
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
    agendaSelectedView () {
      if (this.settings.agendaDefaultView === 'day') {
        return this.$t('agenda.label.viewDay');
      } else if (this.settings.agendaDefaultView === 'week') {
        return this.$t('agenda.label.viewWeek');
      } else {
        return this.$t('agenda.label.viewMonth');
      }
    },
    agendaWeekStartOnLabel () {
      return this.DAY_NAME_BY_ABBREVIATION && this.$t('agenda.settings.label.weekStartsOn',
        {0: this.DAY_NAME_BY_ABBREVIATION[this.settings.agendaWeekStartOn]});
    },
    agendaWorkingTime () {
      return this.settings && this.settings.showWorkingTime && this.settings.workingTimeStart && this.settings.workingTimeEnd
            && this.$t('agenda.settings.label.workingTime',
              {0: this.settings.workingTimeStart, 1: this.settings.workingTimeEnd});
    }
  },
  methods: {
    openDrawer(){
      this.$refs.agendaDrawer.open();
    },
    getDayFromAbbreviation(day) {
      return this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
    },
    updateSettings(settings) {
      this.settings = settings;
    },
  }
};
</script>
