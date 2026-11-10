import "./setup-dom.mjs";
import "./production-loader.mjs";
import { it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { render, screen, fireEvent, cleanup, waitFor, within } from "@testing-library/react";
import { fixtureApi } from "./learning-fixtures.mjs";
import { setSearchParams } from "./production-next-stubs.mjs";
for (const name of ["Element","Node","Document","ShadowRoot","HTMLDivElement","FocusEvent","MouseEvent","KeyboardEvent"]) globalThis[name]=window[name];
globalThis.getComputedStyle=window.getComputedStyle.bind(window);
const { LocaleProvider }=await import("../i18n/locale-provider.tsx");
const { LanguageSwitcher }=await import("../../components/auth/language-switcher.tsx");
const { translate }=await import("../i18n/dictionaries.ts");
const Import=(await import("../../app/(workspace)/vocabulary/import/page.tsx")).default;
const e=React.createElement;
const id="42e08d83-fec4-4e15-9419-7e8da0c798f5";
const originalFetch=globalThis.fetch;
let requests, response, failConfirm, failDestination, api;
const row=(number=1,status="NEW",issues=[])=>({rowNumber:number,cells:["word","nghĩa"],term:"word",vietnameseMeaning:"nghĩa",phoneticIpa:"",partOfSpeech:"other",excluded:false,status,issues,existingMeaning:null,existingIpa:null});
beforeEach(()=>{
 cleanup(); setSearchParams({collectionId:id,mode:"paste"}); requests=[];failConfirm=false;failDestination=false;api=fixtureApi();
 response={delimiter:"COLON",mapping:["term","vietnameseMeaning"],warnings:[],rows:[row()]};
 document.cookie="XSRF-TOKEN=paste-fixture; Path=/";
 globalThis.fetch=async(url,options={})=>{
  const path=new URL(url,"http://localhost").pathname;
  const payload=options.body ? JSON.parse(options.body):null;
  requests.push({path,payload,headers:options.headers});
  if(path.includes("/bulk-import/paste/")) {
   if(path.endsWith("/confirm")) {
    if(failConfirm) return new Response(JSON.stringify({code:"INTERNAL_ERROR",message:"RAW_PRIVATE_ERROR"}),{status:500});
    return Response.json({createdCount:1,linkedCount:2,alreadyPresentCount:3,duplicateCount:4,excludedCount:5,rejectedCount:0,collectionId:id});
   }
   if(path.endsWith("/validate")) return Response.json({...response,rows:payload.rows.map(r=>({...r,cells:[],status:r.excluded?"EXCLUDED":"NEW",issues:[]}))});
   return Response.json(response);
  }
  if(path==="/api/v1/collections/"+id) return new Response(JSON.stringify(failDestination?{code:"NOT_FOUND",message:"RAW_PRIVATE_ERROR"}:{id,title:"Exact destination",wordCount:0}),{status:failDestination?404:200});
  const result=api.respond(url,options); return new Response(result.status===204?null:JSON.stringify(result.body),{status:result.status});
 };
});
afterEach(()=>{cleanup();globalThis.fetch=originalFetch;});
function mount(locale="en"){render(e(LocaleProvider,{initialLocale:locale},e(LanguageSwitcher),e(Import)));}
const button=(locale,key)=>screen.getByRole("button",{name:translate(locale,key)});
async function parse(locale="en"){
 await waitFor(()=>assert.equal(button(locale,"pasteParse").disabled,false));
 fireEvent.click(button(locale,"pasteParse")); await screen.findByTestId("paste-row-1");
}
async function start(locale="en"){
 mount(locale);fireEvent.change(screen.getByLabelText(translate(locale,"pasteText")),{target:{value:"word : nghĩa"}});await parse(locale);
}
const confirms=()=>requests.filter(r=>r.path.endsWith("/paste/confirm"));
for(const locale of ["en","vi-VN"]) {
 it(locale+": exact Study Set destination, dry preview, suggestion and explicit policy",async()=>{
  await start(locale);assert.equal(screen.getByTestId("collection-select").value,id);
  const req=requests.find(r=>r.path.endsWith("/paste/preview"));
  assert.equal(req.payload.collectionId,id);assert.equal(req.payload.delimiter,"AUTO");
  assert.ok(document.body.textContent.includes(translate(locale,"pasteSuggestedDelimiter",{delimiter:translate(locale,"pasteColon")})));
  assert.equal(button(locale,"pasteConfirm").disabled,true);assert.equal(confirms().length,0);
  fireEvent.click(document.getElementById("paste-policy-ack"));assert.equal(button(locale,"pasteConfirm").disabled,false);
 });
 it(locale+": edits invalidate assessment, validate forwards edits and language does not clear draft",async()=>{
  await start(locale);
  const meaning=document.getElementById("paste-vietnameseMeaning-1");
  fireEvent.change(meaning,{target:{value:"Nghĩa đã sửa"}});
  assert.equal(button(locale,"pasteConfirm").disabled,true);
  const other=locale==="en"?"vi-VN":"en";
  fireEvent.change(screen.getByLabelText(/^(Language|Ngôn ngữ)$/),{target:{value:other}});
  assert.equal(document.getElementById("paste-vietnameseMeaning-1").value,"Nghĩa đã sửa");
  assert.equal(document.getElementById("paste-text").value,"word : nghĩa");
  fireEvent.click(button(other,"pasteValidate"));
  await waitFor(()=>assert.equal(button(other,"pasteValidate").disabled,false));
  assert.equal(requests.find(r=>r.path.endsWith("/paste/validate")).payload.rows[0].vietnameseMeaning,"Nghĩa đã sửa");
 });
 it(locale+": accurate result labels, policy payload and exact back link",async()=>{
  await start(locale);fireEvent.click(document.getElementById("paste-policy-ack"));fireEvent.click(button(locale,"pasteConfirm"));
  const result=await screen.findByTestId("paste-result");
  assert.equal(confirms()[0].payload.collectionId,id);assert.equal(confirms()[0].payload.conflictPolicy,"PRESERVE_EXISTING");
  assert.equal(confirms()[0].payload.rows[0].status,undefined);
  for(const [key,n] of [["pasteCreated",1],["pasteLinked",2],["pastePresentCount",3],["pasteDuplicatesCount",4],["pasteExcludedCount",5]]) {
   const dt=within(result).getByText(translate(locale,key));assert.equal(dt.nextElementSibling.textContent,String(n));
  }
  assert.equal(screen.getByRole("link",{name:translate(locale,"learnBackToStudySet")}).getAttribute("href"),"/collections/"+id);
 });
 it(locale+": recoverable confirm failure keeps edits and requires validation before retry",async()=>{
  failConfirm=true;await start(locale);fireEvent.click(document.getElementById("paste-policy-ack"));fireEvent.click(button(locale,"pasteConfirm"));
  await screen.findByText(translate(locale,"pasteRequestFailed"));
  assert.ok(!document.body.textContent.includes("RAW_PRIVATE_ERROR"));assert.equal(document.getElementById("paste-term-1").value,"word");
  assert.equal(button(locale,"pasteConfirm").disabled,true);failConfirm=false;
  fireEvent.click(button(locale,"pasteValidate"));await waitFor(()=>assert.equal(button(locale,"pasteConfirm").disabled,false));
  fireEvent.click(button(locale,"pasteConfirm"));await screen.findByTestId("paste-result");
 });
 it(locale+": invalid rows block confirmation until excluded and revalidated",async()=>{
  response.rows=[row(1,"INVALID",["MISSING_MEANING"]),row(2)];
  await start(locale);fireEvent.click(document.getElementById("paste-policy-ack"));assert.equal(button(locale,"pasteConfirm").disabled,true);
  fireEvent.click(screen.getByLabelText(translate(locale,"pasteIncludeRow",{number:1})));
  fireEvent.click(button(locale,"pasteValidate"));await waitFor(()=>assert.equal(button(locale,"pasteValidate").disabled,false));
  fireEvent.click(document.getElementById("paste-policy-ack"));assert.equal(button(locale,"pasteConfirm").disabled,false);
  const validated=requests.find(r=>r.path.endsWith("/paste/validate"));assert.equal(validated.payload.rows[0].excluded,true);
 });
 it(locale+": ambiguous delimiter warning requires acknowledgement",async()=>{
  response.warnings=["AMBIGUOUS_DELIMITER"];await start(locale);fireEvent.click(document.getElementById("paste-policy-ack"));
  assert.equal(button(locale,"pasteConfirm").disabled,true);fireEvent.click(document.getElementById("paste-warning-ack"));
  assert.equal(button(locale,"pasteConfirm").disabled,false);
 });
 it(locale+": existing, duplicate and ambiguous identities are explicit",async()=>{
  response.rows=[{...row(1,"EXISTING",["PRESERVE_EXISTING"]),existingMeaning:"original"},row(2,"DUPLICATE_INPUT",["FIRST_ROW_KEPT"]),row(3,"AMBIGUOUS_EXISTING",["AMBIGUOUS_EXISTING"])];
  await start(locale);assert.ok(within(screen.getByTestId("paste-row-1")).getByText(translate(locale,"pasteExisting")));
  assert.ok(within(screen.getByTestId("paste-row-2")).getByText(translate(locale,"pasteDuplicate")));
  fireEvent.click(document.getElementById("paste-policy-ack"));assert.equal(button(locale,"pasteConfirm").disabled,true);
 });
 it(locale+": unavailable exact destination never silently falls back",async()=>{
  failDestination=true;mount(locale);await screen.findByText(translate(locale,"pasteDestinationUnavailable"));
  fireEvent.change(document.getElementById("paste-text"),{target:{value:"word : nghĩa"}});
  assert.equal(screen.getByTestId("collection-select").value,id);assert.equal(button(locale,"pasteParse").disabled,true);
  fireEvent.click(button(locale,"learnNoCollectionLibraryOnly"));await parse(locale);
  assert.equal(requests.find(r=>r.path.endsWith("/paste/preview")).payload.collectionId,undefined);
 });
 it(locale+": raw edits invalidate preview and cancel clears without persistence",async()=>{
  await start(locale);fireEvent.change(document.getElementById("paste-text"),{target:{value:"changed : mới"}});
  assert.ok(screen.queryByTestId("paste-row-1")===null);assert.equal(confirms().length,0);
  await parse(locale);fireEvent.click(button(locale,"learnCancel"));assert.equal(document.getElementById("paste-text").value,"");
  assert.equal(confirms().length,0);
 });
}
it("manual delimiter and mapping override reparse original text",async()=>{
 await start();fireEvent.change(document.getElementById("paste-delimiter"),{target:{value:"SEMICOLON"}});
 assert.ok(screen.queryByTestId("paste-row-1")===null);await parse();
 fireEvent.change(document.getElementById("paste-column-0"),{target:{value:"vietnameseMeaning"}});
 fireEvent.change(document.getElementById("paste-column-1"),{target:{value:"term"}});await parse();
 const req=requests.filter(r=>r.path.endsWith("/paste/preview")).at(-1);
 assert.equal(req.payload.delimiter,"SEMICOLON");assert.deepEqual(req.payload.mapping,["vietnameseMeaning","term"]);
 assert.equal(req.payload.text,"word : nghĩa");
});
it("untrusted vocabulary is text and controls have keyboard labels",async()=>{
 response.rows=[{...row(),term:"<script>alert(1)</script>",cells:["<img src=x onerror=alert(1)>"]}];await start();
 assert.equal(document.querySelectorAll("script,img").length,0);
 assert.equal(document.getElementById("paste-term-1").value,"<script>alert(1)</script>");
 assert.equal(document.querySelector('label[for="paste-term-1"]').textContent,translate("en","learnTerm"));
 document.getElementById("paste-term-1").focus();assert.equal(document.activeElement.id,"paste-term-1");
});
it("file workflow remains default and switching modes preserves paste draft",async()=>{
 setSearchParams();mount();assert.equal(button("en","pasteFileMode").getAttribute("aria-pressed"),"true");
 fireEvent.click(button("en","pasteMode"));fireEvent.change(document.getElementById("paste-text"),{target:{value:"draft : nháp"}});
 fireEvent.click(button("en","pasteFileMode"));assert.ok(document.querySelector('input[type="file"]'));
 fireEvent.click(button("en","pasteMode"));assert.equal(document.getElementById("paste-text").value,"draft : nháp");
});


it("oversized raw input stays intact and cannot reach the preview API",async()=>{
 mount(); const value="x".repeat(2*1024*1024+1);
 fireEvent.change(document.getElementById("paste-text"),{target:{value}});
 assert.equal(document.getElementById("paste-text").value.length,value.length);
 assert.equal(button("en","pasteParse").disabled,true);
 assert.equal(requests.filter(r=>r.path.endsWith("/paste/preview")).length,0);
 assert.ok(screen.getByText(translate("en","pasteLimit")));
});
it("preview paginates all rows without dropping edits on other pages",async()=>{
 response.rows=Array.from({length:26},(_,i)=>({...row(i+1),term:"word"+i}));await start();
 assert.ok(screen.queryByTestId("paste-row-26")===null);
 fireEvent.change(document.getElementById("paste-term-1"),{target:{value:"preserved edit"}});
 fireEvent.click(button("en","folderNext"));await screen.findByTestId("paste-row-26");
 fireEvent.click(button("en","folderPrevious"));assert.equal(document.getElementById("paste-term-1").value,"preserved edit");
 fireEvent.click(button("en","pasteValidate"));await waitFor(()=>assert.equal(button("en","pasteValidate").disabled,false));
 assert.equal(requests.find(r=>r.path.endsWith("/paste/validate")).payload.rows.length,26);
});
it("ignored and ragged columns require acknowledgement without removing original cells",async()=>{
 response.warnings=["UNUSED_COLUMNS","RAGGED_ROW"];response.rows[0].cells.push("must remain visible");
 await start();assert.ok(document.body.textContent.includes("must remain visible"));
 fireEvent.click(document.getElementById("paste-policy-ack"));assert.equal(button("en","pasteConfirm").disabled,true);
 fireEvent.click(document.getElementById("paste-warning-ack"));assert.equal(button("en","pasteConfirm").disabled,false);
});
