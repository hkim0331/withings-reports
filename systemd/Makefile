SYSTEMD=/lib/systemd/system/
SERVICE=withings-reports.service
TIMER=withings-reports.timer

install: copy start

copy: ${SERVICE} ${TIMER}
	sudo cp $? ${SYSTEMD}
	sudo systemctl daemon-reload

start:
	sudo systemctl start ${TIMER}
	sudo systemctl enable ${TIMER}

stop:
	sudo systemctl stop ${TIMER}
	sudo systemctl disable ${TIMER}
