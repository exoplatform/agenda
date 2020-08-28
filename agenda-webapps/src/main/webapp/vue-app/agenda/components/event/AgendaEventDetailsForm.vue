<template>
  <form ref="form1">
    <v-container>
      <v-row>
        <label class="float-left mr-4 mt-5">Create</label>
        <input
          ref="titleInput1"
          :placeholder="$t('agenda.eventTitle')"
          type="text"
          name="title"
          class="ignore-vuetify-classes my-3"
          required
        >
        <span class="float-left mr-4 mt-5 ml-4">In</span>
        <input
          ref="selectInput1"
          type="text"
          name="name"
          class="ignore-vuetify-classes my-3"
          required
        >
      </v-row>
      <v-row>
        <v-col>
          <v-row>
            <v-icon size="18" class="mr-11">
              fas fa-map-marker-alt
            </v-icon>
            <input
              ref="locationEvent"
              :placeholder="$t('agenda.eventLocation')"
              type="text"
              name="locationEvent"
              class="ignore-vuetify-classes my-3"
              required
            >
          </v-row>
          <v-row>
            <v-icon size="18">
              fas fa-bell
            </v-icon>
            <label class="ml-11 mr-4 mt-5">{{ $t('agenda.label.notifyUsers') }}</label>
            <input
              ref="timeNotification"
              type="text"
              name="timeNotification"
              class="ignore-vuetify-classes my-3"
              required
            >
            <select class="width-auto my-auto ml-4 pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
              <option>Minutes</option>
              <option>Hours</option>
              <option>days</option>
              <option>weeks</option>
            </select>
            <div>{{ $t('agenda.label.beforeStart') }}</div>
          </v-row>
          <v-row> <a>{{ $t('agenda.addNotification') }}</a></v-row>
          <v-row>
            <v-icon size="18" class="mr-11">
              fas fa-redo-alt
            </v-icon>
            <input
              ref="repeatMode"
              type="text"
              name="repeatMode"
              class="ignore-vuetify-classes my-3"
              required
            >
          </v-row>
          <v-row>
            <v-icon size="18" class="mr-11">
              fas fa-file-alt
            </v-icon>
            <textarea
              ref="autoFocusInput1"
              :placeholder="$t('agenda.description')"
              type="text"
              name="description"
              rows="20"
              maxlength="2000"
              noresize
              class="ignore-vuetify-classes my-3"
            ></textarea>
          </v-row>
          <v-row>
            <v-icon size="18" class="mr-2">
              fas fa-paperclip
            </v-icon>
            <a>{{ $t('agenda.attachFile') }}</a>
          </v-row>
        </v-col>
        <v-col cols="1">
          <v-divider vertical />
        </v-col>
        <v-col>
          <v-row>
            <v-icon size="18" class="mr-2">
              fas fa-users
            </v-icon>
            <input
              ref="addParticipants"
              :placeholder="$t('agenda.addParticipants')"
              type="text"
              name="addParticipants"
              class="ignore-vuetify-classes my-3"
              required
            >
          </v-row>
          <v-row>
            <v-chip
              color="primary"
              close
              pill
            >
              <v-avatar left>
                <v-img src="https://cdn.vuetifyjs.com/images/john.png" />
              </v-avatar>
              New Tweets
            </v-chip>
          </v-row>
        </v-col>
      </v-row>
    </v-container>
  </form>
</template>

<script>
</script>