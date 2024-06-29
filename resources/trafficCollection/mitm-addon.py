# Based on version from projprakt.
from dotenv import load_dotenv
import os
import sys
from datetime import datetime
import mitmproxy
from mitmproxy import http, ctx
from mitmproxy.coretypes import multidict
import psycopg2
from psycopg2.extras import execute_values
import logging

logger = logging.getLogger(__name__)
load_dotenv()


def mdv_to_dict(mdv: multidict) -> dict:
    """
    mitmproxy uses an internal datastructure which allows multiple values for one key.
    This function converts this into a (key, array) dict. It tries to decode the values and keys as well.
    """
    tmp = dict()
    if not mdv:
        return tmp
    for t in mdv.fields:
        # as we only use this for headers and cookies I assume utf-8, else we replace the char
        try:
            key = str(t[0], encoding='utf-8', errors="replace")
        except TypeError:
            key = t[0]
        try:
            tmp[key] = [str(x, encoding='utf-8', errors="replace")
                        for x in t[1:]]
        except TypeError:
            tmp[key] = [str(x) for x in t[1:]]
    return tmp


class MitmAddon:
    def __init__(self):
        self.conn = None
        self.cur = None
        self.run_id = -1
        self.request_id = -1

    # logging tls connection issues to improve ssl pinning deactivation and keep an idea what is failing
    def tls_failed_client(self, data: mitmproxy.tls.TlsData):
        error = data.conn.error
        sni = data.conn.sni
        # logging.error(error + " " + sni)
        self.cur.execute("INSERT INTO request(run,start_time,host,scheme,error) VALUES (%s,NOW(),%s,'https',%s);",
                         (self.run_id, sni, error))
        self.conn.commit()

    def request(self, flow: http.HTTPFlow):
        r: http.HTTPRequest = flow.request
        self.cur.execute(
            "INSERT INTO request (run, start_time, host, port, method, scheme, authority, path, http_version, content_raw) VALUES(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id;",
            (self.run_id, datetime.fromtimestamp(r.timestamp_start), r.pretty_host, r.port, r.method, r.scheme,
             r.authority,
             r.path,
             r.http_version, r.content))
        request_id: int = self.cur.fetchone()[0]
        self.conn.commit()
        self.request_id = request_id
        # logger.info(f"Request id in request: {request_id}")
        # try to decode the content and update the row
        try:
            decoded: str = r.content.decode()
            self.cur.execute(
                "UPDATE request SET content = %s  WHERE id = %s", (decoded, request_id))
            self.conn.commit()
        except ValueError:
            pass
        # headers
        decoded_headers: dict = mdv_to_dict(r.headers)
        if len(decoded_headers) > 0:
            # print([(request_id, k, v) for k, v in decoded_headers.items()])
            execute_values(self.cur, "INSERT INTO header (request, name, values) VALUES %s",
                           [(request_id, k, v) for k, v in decoded_headers.items()])
            self.conn.commit()

        # trailers
        decoded_trailers: dict = mdv_to_dict(r.trailers)
        if decoded_trailers and len(decoded_trailers) > 0:
            # print([(request_id, k, v) for k, v in decoded_trailers.items()])
            execute_values(self.cur, "INSERT INTO trailer (request, name, values) VALUES %s",
                           [(request_id, k, v) for k, v in decoded_trailers.items()])
            self.conn.commit()

    def response(self, flow: http.HTTPFlow):
        r: http.HTTPRequest = flow.response
        request_id = self.request_id
        # logger.info(f"Request ID in response: {request_id}")
        self.cur.execute(
            "INSERT INTO response (run, request, start_time, http_version, status_code, reason, content_raw) VALUES(%s, %s, %s, %s, %s, %s, %s) RETURNING id;",
            (self.run_id, request_id, datetime.fromtimestamp(r.timestamp_start), r.http_version, r.status_code, r.reason,
            r.content))
        response_id: int = self.cur.fetchone()[0]
        self.conn.commit()
        # try to decode the content and update the row
        try:
            decoded: str = r.content.decode()
            self.cur.execute(
                "UPDATE response SET content = %s  WHERE id = %s", (decoded, response_id))
            self.conn.commit()
        except ValueError:
            pass
        # headers
        decoded_headers: dict = mdv_to_dict(r.headers)
        if len(decoded_headers) > 0:
            # print([(request_id, k, v) for k, v in decoded_headers.items()])
            execute_values(self.cur, "INSERT INTO responseheader (response, name, values) VALUES %s",
                           [(response_id, k, v) for k, v in decoded_headers.items()])
            self.conn.commit()

        # trailers
        decoded_trailers: dict = mdv_to_dict(r.trailers)
        if decoded_trailers and len(decoded_trailers) > 0:
            # print([(request_id, k, v) for k, v in decoded_trailers.items()])
            execute_values(self.cur, "INSERT INTO responsetrailer (response, name, values) VALUES %s",
                           [(response_id, k, v) for k, v in decoded_trailers.items()])
            self.conn.commit()

        # cookies
        decoded_cookies: dict = mdv_to_dict(r.cookies)
        if len(decoded_cookies) > 0:
            # print([(request_id, k, v) for k, v in decoded_headers.items()])
            execute_values(self.cur, "INSERT INTO responsecookie (response, name, values) VALUES %s",
                           [(response_id, k, v) for k, v in decoded_cookies.items()])
            self.conn.commit()

    def load(self, loader: mitmproxy.addonmanager.Loader):
        loader.add_option(
            name="run",
            typespec=str,  # For int, I get: "TypeError: Expected <class 'int'> for run, but got <class 'str'>." *shrug*
            default='',
            help="The ID of the run in the database"
        )

        self.conn = psycopg2.connect(host=os.environ['POSTGRES_HOST'] or 'localhost', port=os.environ['HOST_PORT'],
                                     dbname=os.environ['POSTGRES_DB'], user=os.environ['POSTGRES_USER'],
                                     password=os.environ['POSTGRES_PASSWORD'])
        self.cur = self.conn.cursor()

    def running(self):
        if not ctx.options.run or not int(ctx.options.run):
            logger.error("ID of the current run not specified, shutting down.. (Hint: Use `--set run=run_id`)")
            ctx.master.shutdown()
            sys.exit(1)
        self.run_id = int(ctx.options.run)

    def done(self):
        pass
        # the traffic collection start and end will be set by the program itself not by the script
        # self.cur.execute(
        #    "UPDATE runs SET end_time = now() WHERE id=%s", (self.run_id,))
        # self.conn.commit()
        # self.conn.close()


addons = [MitmAddon()]
