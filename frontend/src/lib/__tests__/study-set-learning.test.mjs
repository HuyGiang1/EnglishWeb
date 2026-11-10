import "./setup-dom.mjs";
import "./production-loader.mjs";
import { it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { render, screen, fireEvent, cleanup, waitFor, act } from "@testing-library/react";
import { fixtureApi, word, collection, page as pageResponse } from "./learning-fixtures.mjs";
import { setSearchParams, setParams } from "./production-next-stubs.mjs";
for(const name of ["Element","Node","Document","ShadowRoot","HTMLDivElement","FocusEvent","MouseEvent","KeyboardEvent"]) globalThis[name]=window[name];
globalThis.getComputedStyle=window.getComputedStyle.bind(window);
const { LocaleProvider }=await import("../i18n/locale-provider.tsx");
const { LanguageSwitcher }=await import("../../components/auth/language-switcher.tsx");
const { translate }=await import("../i18n/dictionaries.ts");
const Flashcards=(await import("../../app/(focus)/flashcards/page.tsx")).default;
const Detail=(await import("../../app/(workspace)/collections/[id]/page.tsx")).default;
const e=React.createElement, id="42e08d83-fec4-4e15-9419-7e8da0c798f5";
const originalFetch=globalThis.fetch;
let requests, batches, fail, dueFailed, due, api, delayed;
const term=(name)=>({...word,id:name,term:name});
beforeEach(()=>{
 cleanup();setSearchParams({collectionId:id});setParams({id});api=fixtureApi();requests=[];fail=false;dueFailed=false;due=7;delayed=null;
 batches=[pageResponse([term("alpha"),term("beta")])];
 globalThis.fetch=async(url,options={})=>{
  const parsed=new URL(url,"http://localhost");
  const path=parsed.pathname;requests.push({path,query:parsed.searchParams,method:options.method??"GET"});
  if(path.endsWith("/review/due-count")) {
   if(delayed) return delayed;
   return new Response(JSON.stringify(dueFailed?{code:"INTERNAL_ERROR",message:"RAW_PRIVATE_ERROR"}:{totalDue:due}),{status:dueFailed?500:200});
  }
  if(path.startsWith("/api/v1/collections/") || path.startsWith("/collections/")){
   if(fail) return new Response(JSON.stringify({code:"NOT_FOUND",message:"RAW_PRIVATE_ERROR"}),{status:404});
   if(path.endsWith("/vocabularies")) return Response.json(batches[Number(parsed.searchParams.get("page")??0)]??pageResponse([]));
   return Response.json({...collection,id,title:"My original Study Set",wordCount:99});
  }
  const result=api.respond(url,options);return new Response(result.status===204?null:JSON.stringify(result.body),{status:result.status});
 };
});
afterEach(()=>{cleanup();globalThis.fetch=originalFetch;});
function mount(locale="en",Component=Flashcards){return render(e(LocaleProvider,{initialLocale:locale},e(LanguageSwitcher),e(Component)));}
const b=(locale,key)=>screen.getByRole("button",{name:translate(locale,key)});
async function start(locale="en"){await act(async()=>{mount(locale);});await screen.findByTestId("flashcards-canvas");}
const answer=()=>screen.queryByTestId("flashcards-answer");
for(const locale of ["en","vi-VN"]) {
 it(locale+": Study Set overview shows real counts, scoped actions and existing management",async()=>{
  mount(locale,Detail);const link=await screen.findByRole("link",{name:translate(locale,"studyFlashcards")});
  assert.equal(link.getAttribute("href"),"/flashcards?collectionId="+id);
  assert.equal(screen.getByRole("link",{name:translate(locale,"learnReviewStudySet")}).getAttribute("href"),"/review?collectionId="+id);
  assert.equal(screen.getByRole("link",{name:translate(locale,"learnImportVocabulary")}).getAttribute("href"),"/vocabulary/import?collectionId="+id+"&mode=paste");
  await waitFor(()=>assert.equal(screen.getByTestId("study-due").textContent,"7"));
  assert.equal(screen.getByTestId("study-total").textContent,"99");
  assert.ok(screen.getByRole("button",{name:translate(locale,"learnAddWords")}));
  assert.ok(!requests.some(r=>r.path.endsWith("/review/queue")));
  assert.equal(requests.find(r=>r.path.endsWith("/due-count")).query.get("collectionId"),id);
 });
 it(locale+": only scoped bounded vocabulary is loaded; reveal/next/previous reset answers",async()=>{
  await start(locale);
  assert.equal(screen.getByTestId("flashcards-term").textContent,"alpha");
  assert.ok(answer()===null);
  fireEvent.click(b(locale,"learnRevealAnswer"));assert.ok(answer());
  fireEvent.click(b(locale,"studyNext"));assert.equal(screen.getByTestId("flashcards-term").textContent,"beta");assert.ok(answer()===null);
  fireEvent.click(b(locale,"studyPrevious"));assert.equal(screen.getByTestId("flashcards-term").textContent,"alpha");
  const req=requests.find(r=>r.path.endsWith("/vocabularies"));
  assert.ok(req.path.includes(id));assert.equal(req.query.get("size"),"50");assert.equal(req.query.get("page"),"0");
  assert.ok(requests.every(r=>r.method==="GET"));assert.ok(!requests.some(r=>r.path.endsWith("/review/queue")));
 });
 it(locale+": completion and restart are finite and do not write learning state",async()=>{
  await start(locale);fireEvent.click(b(locale,"studyNext"));fireEvent.click(b(locale,"studyFinish"));
  const done=await screen.findByTestId("flashcards-complete");assert.ok(done.contains(document.activeElement));
  assert.ok(screen.getByRole("heading",{name:translate(locale,"studyComplete")}));
  const count=requests.length;fireEvent.click(b(locale,"studyRestart"));
  assert.equal(screen.getByTestId("flashcards-term").textContent,"alpha");assert.ok(answer()===null);assert.equal(requests.length,count);
  assert.ok(requests.every(r=>r.method==="GET"));
 });
 it(locale+": language switching preserves current card, reveal, order and position",async()=>{
  await start(locale);fireEvent.click(b(locale,"studyNext"));fireEvent.click(b(locale,"learnRevealAnswer"));
  const count=requests.length,other=locale==="en"?"vi-VN":"en";
  fireEvent.change(screen.getByLabelText(/^(Language|Ngôn ngữ)$/),{target:{value:other}});
  assert.equal(screen.getByTestId("flashcards-term").textContent,"beta");assert.ok(answer());
  assert.ok(screen.getByTestId("flashcards-position").textContent.includes("2"));assert.equal(requests.length,count);
  fireEvent.click(b(other,"studyPrevious"));assert.equal(screen.getByTestId("flashcards-term").textContent,"alpha");
 });
 it(locale+": empty set has safe navigation and no fabricated completion",async()=>{
  batches=[pageResponse([])];mount(locale);await screen.findByTestId("flashcards-empty");
  assert.ok(screen.queryByTestId("flashcards-complete")===null);
  assert.equal(screen.getByRole("link",{name:translate(locale,"learnBackToStudySet")}).getAttribute("href"),"/collections/"+id);
  assert.ok(b(locale,"studyRefresh"));
 });
 it(locale+": inaccessible/deleted set keeps exact scope and retry never loads library",async()=>{
  fail=true;mount(locale);await screen.findByRole("alert");assert.ok(!document.body.textContent.includes("RAW_PRIVATE_ERROR"));
  fail=false;fireEvent.click(b(locale,"learnRetry"));await screen.findByTestId("flashcards-canvas");
  assert.ok(requests.every(r=>!r.path.endsWith("/vocabulary")));
  assert.ok(requests.filter(r=>r.path.endsWith("/vocabularies")).every(r=>r.path.includes(id)));
 });
 it(locale+": only the focused canvas handles shortcuts; language/input/button events stay native",async()=>{
  await start(locale);
  fireEvent.keyDown(window,{key:" ",code:"Space"});assert.ok(answer()===null);
  fireEvent.keyDown(screen.getByLabelText(/^(Language|Ngôn ngữ)$/),{key:"Enter",code:"Enter"});assert.ok(answer()===null);
  fireEvent.keyDown(b(locale,"learnRevealAnswer"),{key:" ",code:"Space"});assert.ok(answer()===null);
  const canvas=screen.getByTestId("flashcards-canvas");
  for(const tag of ["input","textarea","select","button","div"]){
   const el=document.createElement(tag);if(tag==="div")el.setAttribute("contenteditable","true");canvas.append(el);
   fireEvent.keyDown(el,{key:" ",code:"Space"});assert.ok(answer()===null);el.remove();
  }
  canvas.focus();assert.equal(document.activeElement,canvas);
  fireEvent.keyDown(canvas,{key:" ",code:"Space"});assert.ok(answer());
  fireEvent.keyDown(canvas,{key:"ArrowRight",code:"ArrowRight"});assert.equal(screen.getByTestId("flashcards-term").textContent,"beta");
  fireEvent.keyDown(canvas,{key:"ArrowLeft",code:"ArrowLeft"});assert.equal(screen.getByTestId("flashcards-term").textContent,"alpha");
 });
 it(locale+": restart retains batch snapshot; explicit refresh reads changed membership",async()=>{
  await start(locale);batches=[pageResponse([term("replacement")])];const count=requests.length;
  fireEvent.click(b(locale,"studyRestart"));assert.equal(screen.getByTestId("flashcards-term").textContent,"alpha");assert.equal(requests.length,count);
  fireEvent.click(b(locale,"studyRefresh"));await waitFor(()=>assert.equal(screen.getByTestId("flashcards-term").textContent,"replacement"));
 });
 it(locale+": next batch is explicit and bounded rather than preloading the library",async()=>{
  batches=[{content:Array.from({length:50},(_,i)=>term("word"+i)),page:0,size:50,totalElements:51,totalPages:2},
   {content:[term("last")],page:1,size:50,totalElements:51,totalPages:2}];
  await start(locale);const count=requests.length;
  for(let i=0;i<50;i++) fireEvent.keyDown(screen.getByTestId("flashcards-canvas"),{code:"ArrowRight",key:"ArrowRight"});
  await screen.findByTestId("flashcards-complete");assert.equal(requests.length,count);
  fireEvent.click(b(locale,"studyNextBatch"));await waitFor(()=>assert.equal(screen.getByTestId("flashcards-term").textContent,"last"));
  const req=requests.filter(r=>r.path.endsWith("/vocabularies")).at(-1);assert.equal(req.query.get("page"),"1");assert.equal(req.query.get("size"),"50");
 });
 it(locale+": due-count failure does not hide vocabulary management and retry is scoped",async()=>{
  dueFailed=true;mount(locale,Detail);await screen.findByText(translate(locale,"studyDueFailed"));
  assert.ok(screen.getByRole("button",{name:translate(locale,"learnAddWords")}));
  assert.equal(screen.getByTestId("study-due").textContent,"—");
  dueFailed=false;fireEvent.click(b(locale,"learnRetry"));await waitFor(()=>assert.equal(screen.getByTestId("study-due").textContent,"7"));
  assert.ok(!document.body.textContent.includes("RAW_PRIVATE_ERROR"));
 });
}
for(const value of ["","invalid","1-1-1-1-1",id+","+id]) it("invalid/absent Flashcard scope cannot fall back: "+value,async()=>{
 setSearchParams(value?{collectionId:value}:"");mount();await screen.findByRole("heading",{name:translate("en","studyChooseSet")});
 assert.equal(requests.length,0);
});
it("vocabulary HTML remains escaped text",async()=>{
 batches=[pageResponse([{...term("<script>alert(1)</script>"),vietnameseMeaning:"<img src=x onerror=alert(1)>"}])];
 await start();fireEvent.click(b("en","learnRevealAnswer"));assert.equal(document.querySelectorAll("script,img").length,0);
 assert.ok(screen.getByTestId("flashcards-answer").textContent.includes("<img"));
});
it("canvas respects prevented/repeated/modified shortcuts",async()=>{
 await start();const canvas=screen.getByTestId("flashcards-canvas");
 for(const options of [{ctrlKey:true},{metaKey:true},{altKey:true},{shiftKey:true},{repeat:true}])fireEvent.keyDown(canvas,{key:" ",code:"Space",...options});
 assert.ok(answer()===null);
 const event=new window.KeyboardEvent("keydown",{key:" ",code:"Space",bubbles:true,cancelable:true});event.preventDefault();
 fireEvent(canvas,event);assert.ok(answer()===null);
});
