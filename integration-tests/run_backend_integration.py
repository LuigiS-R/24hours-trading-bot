#!/usr/bin/env python3
"""Repeatable local integration runner; stdlib-only so it never contacts external providers."""
import base64,json,os,subprocess,sys,time,urllib.request,uuid,signal
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; IT=ROOT/'integration-tests'; LOG=IT/'artifacts'; LOG.mkdir(exist_ok=True)
run_id='it-'+uuid.uuid4().hex[:12]; failures=[]; passed=[]; procs=[]
def http(path,method='GET',body=None,headers={}):
 data=None if body is None else (body if isinstance(body,bytes) else json.dumps(body).encode())
 r=urllib.request.Request('http://127.0.0.1:18080'+path,data=data,method=method,headers={'Content-Type':'application/json',**headers})
 with urllib.request.urlopen(r,timeout=5) as x:return x.status,json.loads(x.read() or b'{}')
def rabbit(path,method='GET',body=None):
 data=None if body is None else json.dumps(body).encode()
 auth=base64.b64encode(b'it_user:it_password_only').decode()
 r=urllib.request.Request('http://127.0.0.1:15673'+path,data=data,method=method,headers={'Content-Type':'application/json','Authorization':'Basic '+auth})
 with urllib.request.urlopen(r,timeout=5) as x:return json.loads(x.read() or b'{}')
def wait(label,fn,seconds=45):
 end=time.monotonic()+seconds; last=None
 while time.monotonic()<end:
  try:
   if fn(): passed.append(label);return True
  except Exception as e:last=e
  time.sleep(.25)
 failures.append((label,'Test-environment defect',str(last)));return False
def assert_that(label,condition,evidence,classification='Critical production defect'):
 (passed if condition else failures).append(label if condition else (label,classification,evidence))
def post_queue(q,payload): rabbit('/api/exchanges/integration/amq.default/publish','POST',{'properties':{'content_type':'application/json'},'routing_key':q,'payload':json.dumps(payload),'payload_encoding':'string'})
def get_queue(q): return rabbit('/api/queues/integration/'+q+'/get','POST',{'count':20,'ackmode':'ack_requeue_false','encoding':'auto','truncate':50000})
def start_jar(name,folder,env):
 f=next((ROOT/folder/'target').glob('*.jar')); out=open(LOG/(name+'.log'),'w'); p=subprocess.Popen(['java','-jar',str(f)],cwd=ROOT/folder,env={**os.environ,**env},stdout=out,stderr=subprocess.STDOUT);procs.append((p,out))
def main():
 try:
  subprocess.run(['docker','compose','-f',str(IT/'compose.yaml'),'up','-d','--wait'],check=True)
  subprocess.run(['javac','-d',str(IT/'fake-alpaca/classes'),str(IT/'fake-alpaca/src/FakeAlpaca.java')],check=True); p=subprocess.Popen(['java','-cp',str(IT/'fake-alpaca/classes'),'FakeAlpaca']);procs.append((p,None))
  wait('infrastructure readiness',lambda:http('/health')[1]['status']=='UP'); wait('rabbitmq readiness',lambda:rabbit('/api/overview').get('rabbitmq_version'))
  # Order Execution consumes a pre-existing ingress queue rather than declaring
  # it. Create that test-only ingress before the listener starts.
  rabbit('/api/queues/integration/orders.approved','PUT',{'durable':True,'auto_delete':False,'arguments':{}})
  for d in ['news-ingestion-service','market-data-service','order-execution-service','account-service']: subprocess.run(['./mvnw','package','-q','-DskipTests'],cwd=ROOT/d,check=True)
  common={'SPRING_RABBITMQ_HOST':'127.0.0.1','SPRING_RABBITMQ_PORT':'5673','SPRING_RABBITMQ_USERNAME':'it_user','SPRING_RABBITMQ_PASSWORD':'it_password_only','SPRING_RABBITMQ_VIRTUAL_HOST':'integration','ALPACA_API_KEY':'fake-key','ALPACA_API_SECRET':'fake-secret'}
  start_jar('news','news-ingestion-service',{**common,'SERVER_PORT':'8082','ALPACA_API_NEWS_STREAM_URL':'ws://127.0.0.1:18081','ALPACA_API_SUBSCRIPTION_MESSAGE':'{"action":"subscribe","news":["*"]}'})
  start_jar('market','market-data-service',{**common,'SERVER_PORT':'8083','ALPACA_API_MARKET_STREAM_URL':'ws://127.0.0.1:18082','ALPACA_API_SUBSCRIPTION_MESSAGE':'{"action":"subscribe","bars":["*"]}'})
  start_jar('order','order-execution-service',{**common,'SERVER_PORT':'8084','ALPACA_API_STREAM_URL':'ws://127.0.0.1:18083','ALPACA_API_ORDER_ENDPOINT':'http://127.0.0.1:18080/v2/orders','ALPACA_API_SUBSCRIPTION_MESSAGE':'{"action":"listen","data":{"streams":["trade_updates"]}}'})
  start_jar('account','account-service',{'DATABASE_URL':'jdbc:postgresql://127.0.0.1:15432/accounts_it','DATABASE_USERNAME':'accounts_it','DATABASE_PASSWORD':'accounts_password_only','KEYCLOAK_ISSUER_URI':'http://127.0.0.1:18080/issuer','SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI':'http://127.0.0.1:18080/jwks','ALPACA_PAPER_TRADING_BASE_URL':'http://127.0.0.1:18080','ALPACA_OAUTH_AUTHORIZE_URL':'http://127.0.0.1:18080/authorize','ALPACA_OAUTH_TOKEN_URL':'http://127.0.0.1:18080/oauth/token','ALPACA_OAUTH_CLIENT_ID':'fake-client','ALPACA_OAUTH_CLIENT_SECRET':'fake-secret','ALPACA_OAUTH_REDIRECT_URI':'http://127.0.0.1:8081/api/v1/alpaca/oauth/callback','SERVER_PORT':'8081'})
  wait('stream subscriptions',lambda:len(http('/control/subscriptions')[1])>=3)
  wait('account migration and authenticated readiness',lambda: urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8081/api/v1/me',headers={'Authorization':'Bearer '+http('/control/token')[1]['token']}),timeout=2).status==200)
  http('/control/send?target=news','POST',b'[{"T":"n","symbols":["AAPL","MSFT"],"headline":"'+run_id.encode()+b'"}]'); wait('news websocket to RabbitMQ',lambda:len(get_queue('financial-news'))>=2)
  http('/control/send?target=market','POST',b'[{"T":"b","S":"AAPL","o":1.1,"h":2.2,"l":1.0,"c":2.0,"v":9}]'); wait('market websocket to RabbitMQ',lambda:len(get_queue('market-data'))>=1)
  order={'ticker':'AAPL','action':'BUY','quantity':1,'order_type':'LIMIT','limit_price':123.45,'testRunId':run_id};post_queue('orders.approved',order); wait('approved order reaches fake broker',lambda:len(http('/control/requests')[1])>=1)
  req=http('/control/requests')[1]; orderreq=[x for x in req if x['path']=='/v2/orders']; assert_that('order request schema and headers',len(orderreq)==1 and 'AAPL' in orderreq[0]['body'] and orderreq[0]['apiKey']=='fake-key',str(orderreq))
  post_queue('orders.approved',order); time.sleep(1); duplicate=[x for x in http('/control/requests')[1] if x['path']=='/v2/orders'];assert_that('duplicate approved order is idempotent',len(duplicate)==1,'two identical deliveries produced %d broker submissions'%len(duplicate),'Critical production defect')
  http('/control/send?target=trade','POST',b'{"stream":"trade_updates","data":{"event":"fill","order":{"client_order_id":"'+run_id.encode()+b'","updated_at":"2026-01-01T00:00:00Z"}}}');wait('trade update reaches order event queues',lambda:len(get_queue('order-events-portfolio'))>=1)
 except subprocess.CalledProcessError as e:
  failures.append(('controlled environment startup','Test-environment defect','command failed: '+str(e)))
 except Exception as e:
  failures.append(('integration runner','Test-environment defect',repr(e)))
 finally:
  for p,o in procs:
   p.terminate();
  for p,o in procs:
   try:p.wait(8)
   except: p.kill()
   if o:o.close()
  subprocess.run(['docker','compose','-f',str(IT/'compose.yaml'),'down','-v','--remove-orphans'])
  (LOG/'summary.json').write_text(json.dumps({'runId':run_id,'passed':passed,'failures':failures},indent=2))
  print(json.dumps({'passed':len(passed),'failed':len(failures),'artifact':str(LOG/'summary.json')},indent=2))
 if failures:sys.exit(1)
if __name__=='__main__':main()
