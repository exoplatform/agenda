<template>
  <v-card
    v-if="displayed"
    class="ma-4 border-radius"
    flat>
    <v-list two-line>
      <v-list-item>
        <v-list-item-content>
          <v-list-item-title class="title text-color">
            <div :class="skeleton && 'skeleton-background skeleton-border-radius skeleton-text-width skeleton-text-height my-2'">
              {{ skeleton && '&nbsp;' || $t('agenda') }}
            </div>
          </v-list-item-title>
          <v-list-item-subtitle class="text-sub-title">
            <div :class="skeleton && 'skeleton-background skeleton-border-radius skeleton-text-width-small skeleton-text-height-fine my-2'">
              <v-list-item v-if="settings" dense>
                <v-list-item-content class="pa-0">
                  <v-list-item-title class="text-wrap">
                    <template>
                      <v-chip
                        class="ma-2"
                        color="primary">
                        <span class="text-capitalize">{{ settings.agendaDefaultView }}</span>
                        <span class="pl-1">{{ $t('agenda.view') }}</span>
                      </v-chip>
                      <v-chip
                        class="ma-2"
                        color="primary">
                        {{ agendaWeekStartOnLabel }}
                      </v-chip>
                      <v-chip
                        v-if="agendaWorkingTime"
                        class="ma-2"
                        color="primary">
                        {{ agendaWorkingTime }}
                      </v-chip>
                    </template>
                  </v-list-item-title>
                </v-list-item-content>
              </v-list-item>
            </div>
          </v-list-item-subtitle>
        </v-list-item-content>
        <v-list-item-action>
          <v-btn
            :class="skeleton && 'skeleton-background'"
            small
            icon
            @click="openDrawer">
            <i v-if="!skeleton" class="uiIconEdit uiIconLightBlue pb-2"></i>
          </v-btn>
        </v-list-item-action>
      </v-list-item>
    </v-list>
    <agenda-user-setting-drawer
      ref="agendaDrawer"
      :settings="settings" />
  </v-card>
</template>

<script>
export default {
  data: () => ({
    id: `Settings${parseInt(Math.random() * 10000)
      .toString()
      .toString()}`,
    settings: {
      agendaDefaultView: 'week',
      agendaWeekStartOn: 'MO',
      showWorkingTime: false,
      workingTimeStart: '08:00',
      workingTimeEnd: '18:00',
    },
    displayed: true,
    skeleton: true,
  }),
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
  created() {
    document.addEventListener('hideSettingsApps', (event) => {
      if (event && event.detail && this.id !== event.detail) {
        this.displayed = false;
      }
    });
    document.addEventListener('showSettingsApps', () => this.displayed = true);
    this.skeleton = false;
    this.$root.$on('agenda-refresh', this.refresh);
    this.refresh();
  },
  methods: {
    refresh() {
      this.$calendarService.getAgendaSettings().then(settings => {
        if (settings && settings.value) {
          this.settings = JSON.parse(settings.value);
        }
      })
        .finally(() => {
          document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
          this.skeleton = false;
        });
    },
    openDrawer(){
      this.$refs.agendaDrawer.open();
    },
    getDayFromAbbreviation(day) {
      return this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
    },
  }
};
</script>