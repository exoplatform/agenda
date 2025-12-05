<template>
  <div class="d-flex ms-5 my-auto">
    <v-menu v-model="menu" offset-y>
      <template #activator="{ on, attrs }">
        <v-btn
          id="agendaDisplayOptions"
          elevation="0"
          class="px-0"
          v-bind="attrs"
          v-on="on">
          <v-icon
            v-if="selectedDispalyOption"
            :class="selectedDispalyOption.icon"
            class="icon-default-color"
            size="20" />
          <v-icon class="ms-1" size="13">mdi-chevron-down</v-icon>
        </v-btn>
      </template>
      <v-list class="pa-0">
        <v-list-item
          v-for="item in dispalyOptions"
          :key="item.value"
          dense
          @click="setDisplayOption(item)">
          <v-list-item-icon class="me-2 my-0 align-self-center">
            <v-icon
              :class="[item.icon, item.value === viewType ? 'primary--text' : 'icon-default-color']"
              size="16" />
          </v-list-item-icon>
          <div :class="item.value === viewType && 'primary--text'">{{ item.label }}</div>
        </v-list-item>
      </v-list>
    </v-menu>
  </div>
</template>
<script>
export default {
  props: {
    calendarType: {
      type: String,
      default: null
    },
  },
  data: () => ({
    menu: false,
  }),
  computed: {
    dispalyOptions() {
      return [
        { value: 'day', icon: 'fas fa-calendar-day', label: this.$t('agenda.label.viewDay') },
        { value: 'week', icon: 'fas fa-calendar-week', label: this.$t('agenda.label.viewWeek') },
        { value: 'month', icon: 'fas fa-calendar-alt', label: this.$t('agenda.label.viewMonth') },
      ];
    },
    selectedDispalyOption() {
      const item = this.dispalyOptions.find(i => i.value === this.calendarType);
      return item ? item : null;
    },
  },
  methods: {
    setDisplayOption(item) {
      this.calendarType = item.value;
      this.$root.$emit('agenda-change-period-type', this.calendarType);
      this.menu = false;
    },
  }
};
</script>
